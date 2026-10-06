# Internal HTTP Contract: account-service maintenance endpoints

account-service 내부 유지보수(재실행 가능한 데이터 이동 등) 엔드포인트. 운영자 명령 surface 가 아니라 기계 호출용 `/internal/**` 이다.
형제 계약: [admin-maintenance-internal.md](./admin-maintenance-internal.md)(admin-service 의 같은 부류).

**호출 방향**: 운영/배포 도구 (client) → account-service (server)
**노출 경로**: `/internal/consumer-pool/*` — 공개 게이트웨이로 노출되지 않음([rules/domains/saas.md](../../../../../../rules/domains/saas.md) S2).
**인증**: account-service `/internal/**` resource-server 사슬 — GAP `client_credentials` Bearer JWT, **`internal.invoke` scope 필수**(`TASK-MONO-422`).
없거나 무효면 `401 UNAUTHORIZED`(fail-closed). `test`/`standalone` 프로파일은 `InternalApiFilter` bypass.
저장소가 시드하는 워크로드 client 중 이 scope 를 가진 것은 iam 넷(`auth-service-client` · `account-service-client` · `admin-service-client` ·
`security-service-client`)뿐이다 — 다른 플랫폼의 워크로드 client 는 401 이다(의도: iam 내부용).

---

## POST /internal/consumer-pool/legacy-moves

**TASK-BE-618 (ADR-MONO-078 A, [multi-tenancy.md § 소비자 계정 풀 § 3](../../../features/multi-tenancy.md#3-기존-계정--한-사이트에만-있으면-같은-id-로-풀로-옮긴다))** —
ADR-MONO-078 이전 모양의 **한 사이트 계정**(소비자 사이트 테넌트에 사는 계정)을 **같은 id 로** `consumer-pool` 로 옮기고 그 사이트 멤버십을 만든다.
id 가 그대로라 사이트 데이터(팬 팔로우 · 스토어 주문 · `artists.account_id`)는 무변경이다.

🔴 **재실행 가능해야 한다** — 내부 프로비저닝은 `ADR-MONO-080` 전까지 사이트 테넌트에 계정을 만든다(§ 3 마지막 문단). 한 번 돌려도 새 사이트 계정이 계속
생기므로, 이 엔드포인트는 몇 번이든 다시 부를 수 있고 이미 옮긴 계정은 다시 건드리지 않는다(후보가 아니다).

**Request Body** (선택 — 본문 없음 = 기본값):

```json
{ "limit": 500, "afterAccountId": null }
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `limit` | int | No | 이번 실행에서 **살펴볼** 후보 수의 상한. 기본 `500`, 범위 `1..1000`. 벗어나면 `400 VALIDATION_ERROR` |
| `afterAccountId` | string | No | 커서. 이 id **보다 큰** 계정 id 부터 살펴본다(id 오름차순). 생략/`null` = 처음부터 |

> 커서가 있는 이유: 건너뛴 계정(두 사이트 · 셀러 …)은 **영구 후보**로 남는다. 커서 없이 `limit` 만 있으면 건너뛸 계정이 `limit` 개 이상일 때 매 실행이
> 같은 머리만 보고 뒤는 영영 보지 못한다. 실행자는 응답의 `nextAfterAccountId` 가 `null` 이 될 때까지 그 값을 다음 요청에 넘긴다.

**Response 200**:

```json
{
  "scanned": 7,
  "moved": 3,
  "skipped": {
    "TWO_SITE": 1, "POOL_EMAIL_EXISTS": 0, "SELLER": 1, "IDENTITY_CONFLICT": 0,
    "OPERATOR_FACETED": 1, "SOCIAL_LINKED": 0, "POOL_CREDENTIAL_EXISTS": 0,
    "CREDENTIAL_TENANT_MISMATCH": 0, "NO_LONGER_CANDIDATE": 0
  },
  "failed": 1,
  "movedAccountIds": ["0199de70-…"],
  "failedAccountIds": ["0199de70-…"],
  "nextAfterAccountId": null
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `scanned` | int | 이번 실행이 살펴본 후보 수(≤ `limit`) |
| `moved` | int | 옮긴 계정 수 |
| `skipped` | object | 건너뛴 사유별 수. **키는 아래 표의 사유 전부**가 항상 실린다(0 포함 — 모양 고정) |
| `failed` | int | 실패(롤백)한 계정 수 — 다음 실행의 후보로 그대로 남는다 |
| `movedAccountIds` | string[] | 옮긴 계정 id, 최대 100개(넘으면 앞 100개) |
| `failedAccountIds` | string[] | 실패한 계정 id, 최대 100개 |
| `nextAfterAccountId` | string \| null | `scanned == limit` 이면 이번에 본 마지막 id(다음 요청의 `afterAccountId`), 아니면 `null`(끝까지 봤다) |

🔴 **응답과 로그에 이메일을 싣지 않는다**(`confidential` PII). 계정 id · 사이트 · 결과만.

**후보**: `accounts.tenant_id` 가 **소비자 사이트**(`Tenant#isConsumerSite` — `B2C_CONSUMER` 이고 `consumer-pool` 이 아닌 테넌트)이고 `status <> 'DELETED'` 인 계정.
`LOCKED` · `DORMANT` 도 후보다(상태는 그대로 옮겨진다).

**건너뛰는 사유** (그 계정은 **아무것도 쓰지 않고** 다음 계정으로 간다):

| 사유 | 판정 | 판정 자리 | 근거 |
|---|---|---|---|
| `SELLER` | 그 사이트에 저장된 `SELLER` 역할(`account_roles`) | account-service | § 3 운영자 측면 표 — 셀러 기계 계정은 옮기지 않는다(`TASK-MONO-745` 는 `ADR-MONO-079`/`TASK-MONO-747` 로 흡수) |
| `TWO_SITE` | 같은 이메일의 계정이 **다른 소비자 사이트**에 있다(상태 무관) | account-service | § 3 — 옮기지 않는다(묶기 `TASK-MONO-743` 은 2026-10-05 대상 0 으로 구현 없이 종결) |
| `POOL_EMAIL_EXISTS` | 같은 이메일의 `consumer-pool` 계정이 있다 | account-service | § 2 공존 금지 — 결함 상태이므로 옮겨서 덮지 않는다 |
| `IDENTITY_CONFLICT` | 그 계정의 신원(`identities`)과 같은 `primary_email` 의 `consumer-pool` 신원이 따로 있거나, 그 신원을 **다른 계정**도 가리킨다 | account-service | 신원 행을 풀로 옮기면 `(tenant_id, primary_email)` UNIQUE 가 깨지거나 남의 계정이 같이 끌려온다 |
| `OPERATOR_FACETED` | admin-service 가 운영자 측면이라 답했다(`oidc_subject` = 이 계정 id, 또는 `identity_id` = 이 계정의 신원) | auth-service → admin-service | § 3 셀프 온보딩 운영자(`TASK-MONO-746`) · 운영자 신원 연결(ADR-MONO-034 U3) |
| `SOCIAL_LINKED` | 그 계정에 `social_identities` 행이 있다 | auth-service | `TASK-BE-617` 결정(2026-10-05 UTC): **사이트 계정으로 남는다** — 이동기는 계속 건너뛴다. 그 사람의 소셜 로그인은 사이트 신원 그대로 같은 계정으로 간다(617 AC-3). 근거 · 되살리는 조건: `multi-tenancy.md` § 3 표 |
| `POOL_CREDENTIAL_EXISTS` | 같은 이메일의 `consumer-pool` 자격이 있다 | auth-service | § 2 — 공존하면 로그인 폼이 한쪽 비밀번호만 본다 |
| `CREDENTIAL_TENANT_MISMATCH` | 그 계정의 자격 행이 풀도 그 사이트도 아닌 테넌트에 있다 | auth-service | 옮길 대상이 아니라 조사 대상이다(BE-507 이전 이상 행) |
| `NO_LONGER_CANDIDATE` | 행을 잠그고 다시 보니 더는 사이트 계정이 아니다(동시 실행이 먼저 옮겼다 · 삭제됐다) | account-service | 멱등 |

마지막 네 auth 사유는 auth-service 이동 호출의 `409` 코드에서 온다([auth-internal.md § consumer-pool/moves](./auth-internal.md#post-internalauthconsumer-poolmoves--자격을-풀로-옮긴다-task-be-618)).
그 밖의 실패(auth-service 5xx · `503`(admin-service 무응답, fail-closed) · 타임아웃 · 예외 · 커밋 실패)는 `failed` 다.

**한 계정의 순서 (계정마다 account_db 트랜잭션 하나 — 한 계정의 실패가 다른 계정에 번지지 않는다)**:

1. `accounts` 행을 잠근다(`SELECT … FOR UPDATE`) — 여전히 사이트 계정인지 다시 본다(아니면 `NO_LONGER_CANDIDATE`).
2. 위 account-service 사유를 다시 판정한다(잠근 뒤의 값으로).
3. 멤버십 `consumer_site_memberships(account, site, ACTIVE, consented_at = accounts.created_at)` — 가입이 곧 그 사이트 동의였다(§ 2).
4. 그 사이트의 `account_roles` 행 **전부**를 `consumer_site_roles(account, site, role_name, granted_by, granted_at)` 로 복사한다(팬 `ARTIST` → § 3).
   저장돼 있던 시드 역할(`FAN` 등)도 그대로 복사한다 — 사이트 principal 의 «저장 역할이 있으면 그것만» 규칙 아래에서 이동 전 세션이 같은 집합을 받으려면
   저장 집합이 그대로 가야 하고(아래 «이동 전 세션»), 풀 principal 에게는 `시드 ∪ consumer_site_roles` 라 중복이 결과를 바꾸지 않는다.
5. 그 `account_roles` 행을 지운다 — 복합 FK `(tenant_id, account_id) → accounts(tenant_id, id)` 때문에 **계정 테넌트 변경 전에**.
6. `tenant_id = 'consumer-pool'`: `accounts`(`version + 1` — 동시에 옛 값을 들고 있던 저장은 낙관적 락으로 실패한다) · `profiles`(`account_id` 로) ·
   그 계정의 `identity_id` 의 `identities` 행(`version + 1`, `identity_id` 가 NULL 이면 없음).
7. **마지막으로** auth-service 이동 호출(`POST /internal/auth/consumer-pool/moves`). 거절·실패하면 예외로 이 트랜잭션 전체가 되돌아간다 — **아무것도 옮겨지지 않는다**.
8. 커밋.

**옮기지 않는 것**:

| 행 | 왜 |
|---|---|
| `account_status_history` | **append-only** — DB 트리거가 UPDATE 를 막는다([audit-heavy.md](../../../../../../rules/traits/audit-heavy.md) A3). 읽기는 전부 `account_id` 로만 해서 404 가 나지 않는다. 옛 행의 `tenant_id` 는 «그 전이가 일어난 때의 테넌트» 라는 사실로 남는다 |
| `auth_db.refresh_tokens` | 미러 행 테넌트는 **세션 테넌트**(= 토큰의 사이트)다 — 이미 목표 모양. 옮기면 refresh 가 `TOKEN_TENANT_MISMATCH` 가 된다(TASK-BE-618 착수 시 정정 ①) |
| `auth_db.social_identities` | 소셜 신원이 있는 계정은 통째로 건너뛴다(`SOCIAL_LINKED`, 정정 ②) |
| `auth_db.oauth2_authorization` | 이동 전 세션의 principal 은 사이트 principal 로 남는다 — 아래 «이동 전 세션» |
| outbox | 이벤트는 이력이다 |

**멱등성 · 실패 창 (AC-5)**: 두 DB 라 단일 트랜잭션이 아니다 — 순서와 멱등성으로 보장한다. auth 호출이 트랜잭션 안의 **마지막 단계**라 auth 가 거절·실패하면
account_db 는 아무것도 바뀌지 않는다. 남는 창은 «auth 커밋 성공 + account 커밋 실패» 하나다: 그 계정은 여전히 사이트 계정이라 다음 실행의 후보로 다시 잡히고,
auth 이동은 «이미 풀이면 성공(`alreadyInPool`)» 이라 그대로 완결된다. 그 창 동안 폼 로그인은 풀 자격 → 풀 principal → 멤버십 없음 → 동의 화면이며,
동의는 풀 계정이 아니라 쓰지 않는다 — 재실행 전까지 그 사람은 들어가지 못한다(토큰 없음, 남의 계정에 붙는 일 없음). 이 경우는 `failed` 로 드러난다.

**이동 전 세션**: 이미 로그인해 있던 사람의 SAS 세션 principal 은 사이트 principal 이라, refresh 때 auth-service 가 `GET /internal/tenants/{site}/accounts/{id}/roles` 를 부른다.
이동 뒤 그 사이트의 `account_roles` 는 비었으므로 그 조회가 넓혀졌다 — [account-internal-provisioning.md § roles GET](./account-internal-provisioning.md#get-internaltenantstenantidaccountsaccountidroles).

**Side Effects**:

- 이벤트 **없음** — 특히 `account.created` 를 내지 **않는다**(이동은 «이 계정이 이 사이트에서 쓸 수 있게 됐다» 가 아니다 — 이미 쓰고 있었다.
  [account-events.md § account.created](../../events/account-events.md#accountcreated)).
- 감사: 계정마다 구조화 애플리케이션 로그 한 줄(계정 id · 사이트 · 결과 · 사유). 운영자 컨텍스트가 없는 기계 호출 배치라 `admin_actions` 행이 아니다 —
  [admin-maintenance-internal.md](./admin-maintenance-internal.md) 의 backfill 과 같은 선례. `account_status_history` 에는 쓰지 않는다(상태 전이가 아니고,
  최신 행을 «현재 상태의 사유» 로 읽는 표면 — `GET …/status` — 이 이동 행을 상태 사유로 보이게 된다).

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 409 | `CONSUMER_POOL_DISABLED` | `iam.consumer-pool.enabled` 가 꺼져 있다 — 실행 전체를 거절(아무것도 쓰지 않음). 꺼진 상태에서 옮기면 사이트로 찾는 표면(§ 5)이 그 계정을 못 찾는다 |
| 400 | `VALIDATION_ERROR` | `limit` 범위 밖 |
| 401 | `UNAUTHORIZED` | `internal.invoke` scope 를 가진 워크로드 JWT 가 없다 |

계정 하나의 실패는 오류 응답이 아니다 — `200` 의 `failed` · `skipped` 로 집계된다.

**실행 주체**: 데모 실행은 `TASK-MONO-744`(재굽기와 함께)의 몫이다.
