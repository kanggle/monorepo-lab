# Internal HTTP Contract: account-service → auth-service

TASK-BE-063 Option A. 신규 계정이 저장된 직후, account-service 가 auth-service 에 자격증명(credential)을 생성하도록 요청한다.

**호출 방향**: account-service (client) → auth-service (server)
**노출 경로**: `/internal/auth/*` — 게이트웨이 퍼블릭 라우트에 노출 금지 ([rules/domains/saas.md](../../../../../../rules/domains/saas.md) S2). 내부 네트워크 외부로 나가선 안 된다.
**인증** (TASK-BE-487, ADR-005 단계 4): 자격증명/액션 `/internal/auth/**` 엔드포인트(credential create·identity-backfill·force-logout·account-id-by-email·consumer-pool/moves)는 **GAP `client_credentials` Bearer JWT** 로만 통과한다. auth-service `SecurityConfig` 가 `permitAll()` 을 `oauth2ResourceServer(jwt)` + `.authenticated()` 로 전환했고(account-service BE-319b 수신 blueprint 복제, self-JWKS·issuer 검증), **추가로 `internal.invoke` scope 를 요구한다**(TASK-MONO-422 — auth 는 시스템·유저 토큰을 모두 발급하는 공유 issuer 라 서명+issuer 만으로 시스템 자격을 구별 못 함; scope 가 discriminator). caller 는 `Authorization: Bearer <token>` 를 첨부한다(account-service=`account-service-client`, admin-service=`admin-service-client`, auth V0019 seed — 둘 다 `internal.invoke` 보유). 미제시/무효/scope 없음 토큰 → `401 {"code":"UNAUTHORIZED"}` (fail-closed). 단 **`GET /internal/auth/jwks` 는 계속 공개**(`permitAll`) — 게이트웨이가 토큰 *검증*용 공개키를 가져가는 경로라 토큰을 제시할 수 없다. `test`/`standalone` 프로파일은 `InternalApiFilter` bypass 로 실 JWT 없이 통과(운영은 항상 fail-closed).

---

## POST /internal/auth/credentials

계정 생성 시점에 호출되는 단방향 쓰기 엔드포인트. auth-service 가 argon2id 해시를 수행하고 `auth_db.credentials` 에 row 를 삽입한다. **plaintext password 는 절대 저장·로그되지 않는다.**

**Request Body**:

```json
{
  "accountId": "string (UUID v7, max 36)",
  "email": "string (RFC 5322)",
  "password": "string (min 8)",
  "tenantId": "string (tenant slug, optional)",
  "identityId": "string (UUID, max 36, optional)"
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `accountId` | string | Yes | 방금 생성된 계정의 UUID. `auth_db.credentials.account_id` 와 일치해야 함 |
| `email` | string | Yes | 로그인 lookup key. lower-case 정규화 후 저장 |
| `password` | string | Yes | 평문. auth-service 가 argon2id 해시 후 `credentials.credential_hash` 에 저장. **caller 는 반드시 HTTPS 내부망으로만 전송할 것** |
| `tenantId` | string | No | 테넌트 slug. 생략 시 `"fan-platform"` 기본값 (TASK-BE-229/313) |
| `identityId` | string | No | **TASK-BE-384 (ADR-MONO-036 M2/P3)** — 계정 생성 시점에 발급된 중앙 `identities.identity_id`. 전달되면 새 credential row 가 born-unified 로 동일 중앙 identity 에 연결된다(`credentials.identity_id`, idempotent·미덮어쓰기 native write). 생략/`null` 이면 born unlinked(account-side mint 실패 fail-soft) — net-zero, 추후 reconcile. |

> **TASK-BE-384 (ADR-MONO-036 born-unified provisioning)** — `identityId` 는 same-origin issuance 의 in-band 전파(P3-A)다. account-service 가 계정 생성 시 `(tenant, email)` 로 mint/reuse 한 중앙 identity 를 이 필드로 넘기고, auth-service 는 `credentials.identity_id` 에 `IS NULL` 가드로 기록한다(덮어쓰기 없음, ADR-MONO-034 § 1.3). email 자동 병합이 아니다.

> **TASK-MONO-263 (ADR-MONO-035 4b-2b / ADR-032 D5 step 4)** — `accountType` 필드(ADR-MONO-021 D2 / TASK-BE-330)는 **제거**되었다. `account_type` JWT 클레임은 더 이상 발급되지 않고 `auth_db.credentials.account_type` 컬럼은 drop 되었다(V0025). 권한 부여는 `roles` 클레임 단일 축으로 수렴한다 — consumer 는 플랫폼별 seed 역할(`CUSTOMER`/`FAN`, RoleSeedPolicy), operator 는 assume-tenant 시점 도메인 역할(OperatorRoleDerivation, BE-376). body 에 `accountType` 이 남아 있어도 무시된다(unknown property).

**Response 201 Created** — 신규 credential 행 삽입 성공:

```json
{
  "accountId": "string",
  "createdAt": "2026-04-19T04:00:00Z"
}
```

**Response 200 OK** — 멱등 재시도: 동일 `(accountId, email)` 조합의 credential 이 이미 존재하고 요청 페이로드가 동일한 계정 컨텍스트임이 확인된 경우 success 로 응답한다. 응답 바디는 201 과 동일한 형태이며 기존 행의 `createdAt` 을 반환한다.

```json
{
  "accountId": "string",
  "createdAt": "2026-04-19T04:00:00Z"
}
```

> **TASK-BE-247 멱등성 보장** — account-service 의 `@Transactional` 롤백 + auth-service 커밋 race(half-commit)가 재현될 때, 동일 signup 페이로드의 재시도가 200 을 수신하여 signup 을 정상 완료시킨다. 멱등 조건: `existingRow.accountId == request.accountId AND existingRow.email == request.email`. **password 는 비교하지 않는다** — argon2id 해시는 매 호출마다 달라지며 평문 비교는 보안 결함이다.

**Response 409 Conflict** — 충돌: 동일 `accountId` 에 다른 `email` 이 등록되어 있거나, 동일 `email` 에 다른 `accountId` 가 등록된 경우 — 시그니처 불일치 → 진성 중복:

```json
{
  "code": "CREDENTIAL_ALREADY_EXISTS",
  "message": "Credential already exists for this account",
  "timestamp": "2026-04-19T04:00:00Z"
}
```

동시성 대응: 선행 `existsByAccountId` 조회로 1차 차단하고, unique 제약 위반 시 2차로 409 로 변환한다 (DataIntegrityViolationException → 409). 멱등 경로에서는 기존 행과 `(accountId, email)` 이 일치하면 409 대신 200 을 반환한다.

**Response 400 Bad Request** — validation 실패 (email 형식, password 최소 길이 등). 게이트웨이/경계 validator 가 잡지 못한 케이스.

**Response 5xx / 타임아웃**: caller 는 **fail-closed** 로 처리한다. 즉 account-service 의 `SignupUseCase` 는 `@Transactional` 롤백으로 계정·프로필 row 를 폐기한다.

---

## POST /internal/auth/credentials/identity-backfill

**TASK-BE-386 (ADR-MONO-036 P4, M4)** — 프로덕션 데이터 reconciliation 의 auth_db 절반. account-service 가 account_db 에서 해석한 `account_id → identity_id` 매핑을 **일괄(batch)** 전달하면, auth-service 가 각 credential row 의 `identity_id` 를 채운다. M2 의 `assignIdentityId` writer 를 재사용한다 — native, `IS NULL` 가드, **멱등·무덮어쓰기**(ADR-MONO-034 § 1.3). cross-DB 라 마이그레이션으로 못 하는 절반을 이 푸시-기반 엔드포인트가 담당한다. **same-origin 전파이며 email 자동 병합이 아니다.**

> AIP-136 콜론 verb 대신 하이픈 경로(`identity-backfill`)를 쓴다 — 클래스 레벨 `/internal/auth` prefix 와 결합 시 `:verb` 가 `PathPatternParser` 에서 오판되기 때문(account-service `BulkAccountController` 동일 이슈).

**Request Body**:

```json
{
  "items": [
    { "accountId": "string (UUID v7, max 36)", "identityId": "string (UUID, max 36)" }
  ]
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `items` | array | Yes | 1개 이상의 `(accountId, identityId)` 쌍. 비어 있으면 400 |
| `items[].accountId` | string | Yes | credential row 의 lookup key (`credentials.account_id`, UNIQUE) |
| `items[].identityId` | string | Yes | 해당 account 가 이미 해석한 중앙 `identities.identity_id` (account_db) |

**Response 200 OK** — 처리 결과 요약. 이미 연결된(또는 credential 없는) 쌍은 `updated` 에 집계되지 않는다(net-zero):

```json
{ "requested": 120, "updated": 37 }
```

**멱등성**: 동일 배치를 재전송하면 `IS NULL` 가드로 이미 채워진 row 는 0건 갱신 → `updated` 가 줄어들 뿐 부작용 없음. operator(admin_db) 절반은 이 엔드포인트가 건드리지 않는다 — opt-in 감사 link 표면이 담당(권한상승 가드). account_db 절반(orphan identity mint+link)은 account-service Flyway **V0024** 마이그레이션이 담당.

---

## POST /internal/auth/consumer-pool/moves — 자격을 풀로 옮긴다 (TASK-BE-618)

**TASK-BE-618 (ADR-MONO-078 A, [multi-tenancy.md § 소비자 계정 풀 § 3](../../../features/multi-tenancy.md#3-기존-계정--한-사이트에만-있으면-같은-id-로-풀로-옮긴다))** —
한 사이트 계정을 풀로 옮기는 일괄 이동([account-maintenance-internal.md](./account-maintenance-internal.md))의 auth_db 절반. account-service 가 계정 하나의
account_db 트랜잭션 **마지막 단계**로 부른다. 옮기는 것은 **`credentials.tenant_id` 하나**(사이트 → `consumer-pool`)뿐이다.

**Request Body**:

```json
{ "accountId": "string (UUID, max 36)", "siteTenantId": "string (tenant slug)" }
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `accountId` | string | Yes | 옮기는 계정(`credentials.account_id`, UNIQUE) |
| `siteTenantId` | string | Yes | 그 계정이 지금 사는 소비자 사이트. 자격 행의 `tenant_id` 가 이 값이어야 옮긴다 |

**판정 (auth_db 트랜잭션 하나, 위에서부터 처음 맞는 것)**:

| # | 조건 | 응답 | 쓰기 |
|---|---|---|---|
| 1 | 그 계정의 자격 행이 이미 `consumer-pool` | `200 {"moved": false, "alreadyInPool": true}` | 없음 — **멱등**(이전 실행의 auth 커밋 뒤 account 커밋이 실패한 경우를 완결) |
| 2 | 그 계정의 자격 행이 없다 | `200 {"moved": false, "alreadyInPool": false}` | 없음 — 옮길 자격이 없다(소셜 전용 등). 계정 쪽 이동은 계속된다 |
| 3 | 자격 행의 `tenant_id` ≠ `siteTenantId` | `409 POOL_MOVE_CREDENTIAL_TENANT_MISMATCH` | 없음 |
| 4 | 그 계정의 `social_identities` 행이 **하나라도** 있다(테넌트 무관) | `409 POOL_MOVE_SOCIAL_LINKED` | 없음 |
| 5 | 같은 이메일의 `consumer-pool` 자격(다른 계정)이 있다 | `409 POOL_MOVE_CREDENTIAL_EXISTS` | 없음 |
| 6 | admin-service 가 운영자 측면이라 답했다([auth-to-admin.md § facet](./auth-to-admin.md#get-internaloperatorsfacet--운영자-측면-판정-task-be-618)) — `accountId` 와 자격 행의 `identity_id` 로 묻는다 | `409 POOL_MOVE_OPERATOR_FACETED` | 없음 |
| 7 | admin-service 에 물을 수 없다(5xx · 타임아웃 · circuit-open · 본문 이상) | `503 SERVICE_UNAVAILABLE` | 없음 — **fail-closed** |
| 8 | 그 밖 | `200 {"moved": true, "alreadyInPool": false}` | `credentials.tenant_id = 'consumer-pool'`, `version + 1`(동시에 옛 값을 들고 있던 저장은 낙관적 락으로 실패) |

409 본문은 표준 오류 모양이다: `{"code": "POOL_MOVE_SOCIAL_LINKED", "message": "...", "timestamp": "..."}`. account-service 는 `code` 를 건너뛰기 사유로 바꾼다
(`POOL_MOVE_` 접두어를 뗀 이름 — `SOCIAL_LINKED` · `POOL_CREDENTIAL_EXISTS` · `OPERATOR_FACETED` · `CREDENTIAL_TENANT_MISMATCH`). 모르는 409 코드와 그 밖의 비-2xx 는 실패다.

**옮기지 않는 것과 그 이유**:

- **`refresh_tokens`** — 미러 행의 `tenant_id` 는 계정의 테넌트가 아니라 **세션 테넌트 = 그 토큰의 `tenant_id`** 다(`AuthorizationSessionTenant`, `TASK-BE-604`).
  풀 principal 의 세션 테넌트는 요청한 사이트이므로(§ 4 «refresh»), 사이트 계정의 기존 행(`tenant_id` = 그 사이트)은 **이미 목표 모양**이다.
  `consumer-pool` 로 바꾸면 `RefreshTokenUseCase` 가 제출된 토큰의 사이트와 행의 `consumer-pool` 을 비교해 `TOKEN_TENANT_MISMATCH` 를 낸다 — 블랙리스트 키도 행 테넌트다
  (TASK-BE-618 착수 시 정정 ①).
- **`social_identities`** — 소셜 로그인은 `(사이트 테넌트, provider, provider_user_id)` 로 신원을 찾는다. 행을 풀로 옮기면 조회가 비어 «새 소셜 가입» 으로 가고,
  그 가입은 같은 이메일의 풀 계정에 막혀 그 사람의 소셜 로그인이 끊긴다. 옮기지 않고 계정만 옮겨도 신원 행과 계정의 테넌트가 갈린다. 그래서 신원이 있는 계정은
  판정 4 로 **통째로** 건너뛴다(정정 ②). 🔵 `TASK-BE-617` 결정(2026-10-05 UTC): 소셜 조회를 풀-먼저로 바꿨지만 판정 4 는 **그대로 둔다** — 그 계정들은 사이트 계정으로
  남고 사이트 신원 그대로 로그인된다(근거 · 되살리는 조건: `multi-tenancy.md` § 3 표의 `social_identities` 행).
- `oauth2_authorization`(SAS 세션) — 이동 전 세션의 principal 은 사이트 principal 로 남는다.

**Side Effect**: 이벤트 없음. 감사는 구조화 로그(계정 id · 사이트 · 결과) — 이메일은 싣지 않는다.

---

## ~~GET /internal/auth/credentials/{accountId}/email~~ — REMOVED (TASK-MONO-299)

> **제거됨 (ADR-MONO-040 Phase 3 part B / TASK-MONO-299)** — Phase-2 의 account_id → email read-only 엔드포인트(login-time operator-token exchange 의 DUAL-KEY email fallback 용)는 운영자 해석이 account_id 단독으로 전환되면서 제거되었다(`admin_operators.oidc_subject` 가 part A 로 account_id backfill 됨). 유일한 consumer 였던 admin-service `AuthServiceClient.resolveOperatorEmail` 도 함께 제거되었다. **역방향** `POST /internal/auth/credentials/account-id-by-email`(email → account_id, part A backfill 도구; admin-service 가 호출)은 **유지**된다 — canonical 정의는 [admin-to-auth.md](./admin-to-auth.md) §`POST /internal/auth/credentials/account-id-by-email`.

---

## Caller Constraints (account-service 측)

- 타임아웃: connect 3s, read 15s (TASK-BE-247: cold-start race 마스킹을 위해 5s → 15s 상향)
- 재시도: 2회 (지수 백오프 + jitter). **4xx 는 재시도 금지**
- Circuit breaker: 실패율 50% / 10초 window → open → 10초 half-open
- 409 는 caller 가 `AccountAlreadyExistsException` 으로 변환 (동시 signup 경합 시 일관된 409 응답)
- 5xx / timeout / circuit-open 은 `AuthServiceUnavailable` 로 승격되어 signup 전체가 롤백되고 호출자에게 전파됨
- **`consumer-pool/moves` (TASK-BE-618)**: 같은 타임아웃·재시도·circuit breaker. 409 의 `code` 는 `AuthServicePort.CredentialPoolMoveRefused`(건너뛰기 사유)로,
  그 밖의 실패는 `AuthServiceUnavailable` 로 — 둘 다 그 계정의 account_db 트랜잭션을 되돌린다

## Server Constraints (auth-service 측)

- `credential_hash` 는 argon2id (m=65536, t=3, p=1)
- `email` 은 lower-case 정규화 후 유니크 제약 `credentials.email` 에 저장 (V0006 마이그레이션)
- `account_id` 컬럼도 유니크. 둘 중 어느 쪽이든 충돌하면 409
- 로그·감사에 `password`, `credential_hash` 기록 금지 ([rules/traits/regulated.md](../../../../../../rules/traits/regulated.md) R4). 분류 등급 **restricted**

---

## 단기 방어 (short-circuit)

auth-service `LoginUseCase` 는 credential lookup 이 null 을 반환하면 NPE 대신 `CredentialsInvalidException` 을 던진다. 이는 본 태스크가 끝난 이후에도 fail-safe 로 유지한다 — 방어 제거는 contract·DB 상태의 일관성이 충분히 검증된 후.
