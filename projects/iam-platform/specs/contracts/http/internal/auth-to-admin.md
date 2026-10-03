# Internal HTTP Contract: auth-service → admin-service

auth-service가 **assume-tenant** RFC 8693 token-exchange 발급 시점에 운영자의 D1 assignment 를 확인한다 (ADR-MONO-020 § 3.3 step 2, D2).

**호출 방향**: auth-service (client) → admin-service (server)
**노출 경로**: `/internal/operator-assignments/*` · `/internal/operators/facet`(TASK-BE-618) — 게이트웨이 퍼블릭 라우트에 노출 금지 ([rules/domains/saas.md](../../../../../../rules/domains/saas.md) S2)
**인증** (TASK-BE-327 호출측/수신측): `Authorization: Bearer <IAM client_credentials JWT>` — auth-service 가 `auth-service-client` 로 IAM `/oauth2/token` 에서 발급받아 첨부하고 ([IamClientCredentialsTokenProvider] 재사용), admin-service 가 IAM JWKS 서명 + issuer 로 검증한다. 정적 토큰 경로 없음. JWT 미제시/무효 시 모든 `/internal/**` 요청은 401 `UNAUTHORIZED` 로 fail-closed (account-service 의 `/internal/**` 체인 미러링).

> **TASK-BE-327 (ADR-MONO-020 D2)** — 이 edge 는 assume-tenant 발급 시점의 **1회성(one-shot) read** 이다. 도메인→IAM 의 per-request callback 이 **아니다** (ADR-020 § 3.1 은 후자만 금지한다; assignment store(D1)·assume-tenant 발급(D2)·entitled_domains 도출(D3) 은 모두 IAM 내부에 머무르므로 auth↔admin 조율은 IAM-internal). admin_actions row 를 쓰지 않는다 (read-only — ADR-014 token-exchange "not audited" 규칙과 동일).

---

## GET /internal/operator-assignments/check

선택된 customer tenant 에 대한 운영자의 **effective tenant scope** 포함 여부를 확인한다. auth-service 가 assume-tenant 토큰을 발급하기 전 fail-closed 게이트로 호출한다.

> **TASK-BE-338 (ADR-MONO-020 D3 amendment 2026-06-05)** — 응답에 `orgScope` 필드가 **additive** 로 추가되었다(선택 assignment 의 per-assignment 데이터-스코프). 기존 `assigned` 필드·판정 규칙·상태코드는 **byte-불변**. auth-service `TenantClaimTokenCustomizer.customizeForAssumeTenant` 가 이 값을 assume-tenant 토큰 `org_scope` claim 으로 주입한다(하드코딩 `["*"]` BE-337 브리지 대체; `null`/empty → `["*"]` = net-zero). 오직 erp 가 subtree-root 를 소비(TASK-ERP-BE-008).

> **TASK-MONO-299 (ADR-MONO-040 Phase 3 part B 2026-06-18)** — 운영자 row 조회는 **account_id 단독**이다. Phase 2 의 임시 **DUAL-KEY** email fallback 과 `X-Subject-Email` 헤더는 **제거**되었다. part A(TASK-MONO-298)가 `admin_operators.oidc_subject` 를 account_id 로 backfill 했으므로 admin-service 는 `oidcSubject`(=account_id, SAS `sub`)로 직접 조회한다. (실배포 전제: part-A backfill 이 선행돼야 한다 — 미migrate 운영자는 fallback 제거 후 fail-closed 된다.)

**Query Parameters**:

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `oidcSubject` | string (UUID) | Yes | 운영자의 IAM OIDC subject (`sub` = account_id, ADR-040 Phase 2). `admin_operators.oidc_subject` 로 운영자 row 를 fail-closed 조회(account_id 단독 키, TASK-MONO-299) |
| `tenantId` | string (slug) | Yes | 선택된(assume 대상) customer tenant id |

**Response 200**:
```json
{
  "assigned": true,
  "orgScope": ["dept-sales"]
}
```

파트너십-파생 host reach(ADR-MONO-045)인 경우에만 `delegatedScope` 블록이 additive 하게 실린다:
```json
{
  "assigned": true,
  "delegatedScope": { "domains": ["wms"], "roles": ["WMS_OUTBOUND_OPERATOR"] }
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `assigned` | boolean | 운영자의 effective tenant scope 가 `tenantId` 를 포함하면 `true`, 아니면 `false` |
| `orgScope` | array\<string\> \| null | **TASK-BE-338 (ADR-MONO-020 D3 amendment) — additive.** 선택된 assignment 의 per-assignment **데이터-스코프**(운영자가 그 테넌트에서 act 가능한 부서 **subtree-root id** 들). `null` ⟺ `["*"]` = 테넌트 전체(**net-zero** 기본) — `org_scope` 컬럼 미설정, 명시적 assignment row 부재(legacy home-tenant / platform-scope), 그리고 모든 `assigned=false` 케이스에서 `null`. 명시적 빈 배열 `[]` = zero-scope(NULL 과 구분, verbatim 반환). auth-service 가 `null`/empty/부재 → `["*"]` 로 기본 처리(graceful, 구버전 admin 호환). |
| `delegatedScope` | object `{domains:[], roles:[]}` \| absent | **TASK-BE-477 (ADR-MONO-045 D3/D5) — additive.** 오직 **파트너십-파생 host reach** 케이스에만 존재하는 cross-org confinement 블록: partner 테넌트 B 의 participant 인 운영자가 host 테넌트 A(=`tenantId`)를 assume 할 때 얻는 **capped** 도메인-운영 스코프 = `delegated_scope ∩ participant_scope ∩ host-holds`. **정상(비-파트너십) assignment 와 모든 `assigned=false` 케이스에는 이 필드가 부재**한다(`@JsonInclude(NON_NULL)` 로 omit — 기존 `{assigned, orgScope}` shape 은 byte-불변). auth-service(step 2b)가 이 값으로 assume-tenant 토큰의 `entitled_domains` 를 `domains` 와 교집합하고 role 을 `roles` 로 캡한다. **admin scope 는 절대 확장되지 않는다** — cross-org actor 는 host 에서 `effectiveAdminScope` 공집합(→ `/api/admin/**` 403). 이 필드가 실린다고 해서 운영자가 host 를 administer 할 수 있는 것은 아니다. |

**판정 규칙** (server-side, admin-service):

1. **account_id 조회 (TASK-MONO-299)**: `oidcSubject`(account_id) 로 `admin_operators` row 조회 (`AdminOperatorPort.findByOidcSubject`). miss OR 비-`ACTIVE` → `assigned=false`, `orgScope=null` (fail-closed; 운영자 존재 여부를 boolean 너머로 노출하지 않는다).
2. platform-scope 운영자 (`tenant_id == '*'`, `isPlatformScope()`) → 비어있지 않은 모든 `tenantId` 에 대해 `assigned=true` (sentinel 이 모든 tenant 를 부여; assumed 토큰은 여전히 선택된 구체 `tenant_id` 를 운반한다 — `'*'` 토큰은 절대 발급되지 않는다). 명시적 assignment row 가 없으므로 `orgScope=null` (→ `["*"]`).
3. 그 외 → `assigned = TenantScopeResolver.resolveEffectiveTenantScope(internalId, homeTenant).contains(tenantId)` (D1 assignment rows ∪ {legacy home tenant} — BE-326 dual-read). `assigned=true` 시 `orgScope = operator_tenant_assignment.{(operatorInternalId, tenantId)}.org_scope` (해당 (운영자, 선택 테넌트) assignment row 의 `org_scope`; row 부재 또는 컬럼 NULL → `null`).
4. `tenantId` blank → `assigned=false`, `orgScope=null`.
0. **`admin_operators.confined_tenant_id` — 한 테넌트로 묶인 운영자 (`TASK-MONO-751`, 2026-10-03 소유자 결정 «데모 운영자는 팬 전용으로»).** 운영자 행을 찾고 ACTIVE 를 확인한 직후, **2번(platform-scope)보다 먼저**: 이 컬럼이 비-NULL 이고 `tenantId` 와 다르면 `assigned=false`. 같으면 나머지 규칙이 그대로 판정한다(좁히기만 — 열지 않는다). `NULL`(기존 모든 운영자)은 이 규칙에 걸리지 않는다. 쓰는 곳: 데모 플랫폼 운영자(`'*'`, `confined_tenant_id='fan-platform'`) — `fan-platform` 만 assume 가능.
5. **`tenantId == fan-platform` — 플랫폼 운영자 전용 (`TASK-MONO-750`, `ADR-MONO-079` D4-A 라이더 R3).** 2번(platform-scope)에 걸리지 않은 운영자 — 즉 **고객사 운영자** — 는 `fan-platform` 에 대해 **항상 `assigned=false`** 다. assignment row 가 있어도(이 surface 가 생기기 전에 만들어졌든 직접 SQL 이든), 파트너십-파생 host reach(3·`delegatedScope`) 가 있어도 같다 — 이 판정은 3번과 파트너십 분기 **앞에서** 내려진다. 이유: `fan-platform` 이 `fan` 도메인을 구독하므로 이 테넌트를 assume 한 토큰은 `FAN_OPERATOR` 를 파생받고, 그 역할로 열리는 길(artist-service 디렉터리 관리)은 플랫폼 운영자 몫으로만 열렸다. 플랫폼 운영자는 2번으로 지금처럼 `assigned=true`.

**Side Effect**: 없음 (read-only — `admin_actions` row 미기록).

**Errors**:

| Status | 조건 |
|---|---|
| 401 `UNAUTHORIZED` | IAM client_credentials JWT 미제시/무효 (`/internal/**` 체인 fail-closed) |
| 400 `VALIDATION_ERROR` | `oidcSubject`/`tenantId` 파라미터 누락 |

운영자 미존재/비-ACTIVE/미할당은 모두 `200 {assigned:false}` 로 응답한다 (열거 방어; 별도 4xx 로 구분하지 않는다).

---

## GET /internal/operators/facet — 운영자 측면 판정 (TASK-BE-618)

**TASK-BE-618 (ADR-MONO-078 A, [multi-tenancy.md § 소비자 계정 풀 § 3](../../../features/multi-tenancy.md#3-기존-계정--한-사이트에만-있으면-같은-id-로-풀로-옮긴다))** —
한 사이트 계정을 풀로 옮기기 전에, 그 계정에 **운영자 측면**이 붙었는지 auth-service 가 묻는다
([auth-internal.md § consumer-pool/moves](./auth-internal.md#post-internalauthconsumer-poolmoves--자격을-풀로-옮긴다-task-be-618) 판정 6).
운영자 측면이 붙은 계정은 이 단계에서 옮기지 않는다(§ 3 운영자 측면 표 — 셀프 온보딩 운영자는 `TASK-MONO-746`).
account-service 가 아니라 auth-service 가 묻는 이유: account-service 는 admin-service 를 부르지 않는다(반대 방향 의존 `admin → account` 가 이미 있어 순환).

**Query Parameters**:

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `accountId` | string (UUID) | Yes | 옮기려는 계정 id |
| `identityId` | string (UUID) | No | 그 계정 자격 행의 `identity_id`(중앙 신원). 없거나 공백이면 신원 축은 묻지 않는다 |

**Response 200**:
```json
{ "operatorFaceted": true }
```

**판정 (read-only)**: `admin_operators` 행이 **하나라도**(상태 무관 — `SUSPENDED` 운영자도 측면이다) 다음 중 하나를 만족하면 `true`:

1. `oidc_subject = accountId` — ADR-MONO-044 D5 셀프 온보딩 운영자(운영자 `sub` = 그 소비자 계정 id).
2. `identityId` 가 주어졌고 `identity_id = identityId` — 운영자 신원 연결(ADR-MONO-034 U3, `LinkOperatorIdentityUseCase`). 신원 행을 풀로 옮기면 그 운영자의 신원이
   풀 신원이 되므로 운영자 측면이다.

그 밖 `false`. 운영자 존재를 boolean 너머로 드러내지 않는다(어느 축이 맞았는지 답하지 않는다).

**Side Effect**: 없음 (read-only — `admin_actions` row 미기록).

**Errors**:

| Status | 조건 |
|---|---|
| 401 `UNAUTHORIZED` | IAM client_credentials JWT 미제시/무효 (`/internal/**` 체인 fail-closed) |
| 400 `VALIDATION_ERROR` | `accountId` 누락 |

**Caller (auth-service) — fail-CLOSED**: 아래 Caller Constraints 와 같은 타임아웃·재시도·circuit breaker(별도 breaker 이름 — 이동 배치의 실패가 assume-tenant 게이트를
열지 않는다). 답을 못 받으면(4xx · 5xx · 타임아웃 · circuit-open · 본문 이상) **옮기지 않는다** → `503 SERVICE_UNAVAILABLE`. 모른 채 옮기면 운영자 계정을 풀로 끌고 간다.

---

## Caller Constraints (auth-service 측 — **fail-CLOSED**)

- 타임아웃: 연결 3s, 읽기 5s (account edge 와 동일 정책)
- 재시도: 2회 (지수 백오프 + jitter). 4xx 는 재시도 금지
- Circuit breaker: 실패율 50% / 10초 sliding window → open → half-open
- **⚠️ fail-CLOSED**: admin-service 장애 시(`assigned=false` / 4xx / 5xx / circuit-open / timeout / IO 모두) **assume-tenant 발급 거부** (`AssumeTenantDeniedException` → RFC 8693 `invalid_grant` / 400, 토큰 미발급). 이 게이트는 **절대 fail-soft 하지 않는다** — account-service `entitled_domains` 도출(fail-soft)과 정반대 정책이다. 인가 게이트이므로 가용성에 의존해 토큰을 발급해서는 안 된다 (격리 위반 = isolation breach).
- **감사 기록 없음**: read 이므로 "audit first" 가 적용되지 않는다 (admin-to-account 의 lock/unlock 명령과 다름).
