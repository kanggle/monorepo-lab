# Internal HTTP Contract: auth-service → admin-service

auth-service가 **assume-tenant** RFC 8693 token-exchange 발급 시점에 운영자의 D1 assignment 를 확인한다 (ADR-MONO-020 § 3.3 step 2, D2).

**호출 방향**: auth-service (client) → admin-service (server)
**노출 경로**: `/internal/operator-assignments/*` · `/internal/operators/facet`(TASK-BE-618) · `/internal/operators/console-eligibility` · `/internal/operator-invitations/*`(TASK-MONO-772) — 게이트웨이 퍼블릭 라우트에 노출 금지 ([rules/domains/saas.md](../../../../../../rules/domains/saas.md) S2)
**인증** (TASK-BE-327 호출측/수신측): `Authorization: Bearer <IAM client_credentials JWT>` — auth-service 가 `auth-service-client` 로 IAM `/oauth2/token` 에서 발급받아 첨부하고 ([IamClientCredentialsTokenProvider] 재사용), admin-service 가 IAM JWKS 서명 + issuer 로 검증한다. 정적 토큰 경로 없음. JWT 미제시/무효 시 모든 `/internal/**` 요청은 401 `UNAUTHORIZED` 로 fail-closed (account-service 의 `/internal/**` 체인 미러링).

> **TASK-BE-327 (ADR-MONO-020 D2)** — 이 edge 는 assume-tenant 발급 시점의 **1회성(one-shot) read** 이다. 도메인→IAM 의 per-request callback 이 **아니다** (ADR-020 § 3.1 은 후자만 금지한다; assignment store(D1)·assume-tenant 발급(D2)·entitled_domains 도출(D3) 은 모두 IAM 내부에 머무르므로 auth↔admin 조율은 IAM-internal). admin_actions row 를 쓰지 않는다 (read-only — ADR-014 token-exchange "not audited" 규칙과 동일).

> **TASK-MONO-772 (ADR-MONO-080 D6)** — 이 edge 에 **쓰기 하나**가 생긴다: `POST /internal/operator-invitations/accept`(초대 수락 — 운영자 행을 만든다, 감사 행을 쓴다). 나머지 셋(`console-eligibility` · `operator-invitations/preview` · 기존 둘)은 읽기다.
> 이 edge 의 방향(auth → admin)은 그대로다 — account-service 는 admin-service 를 부르지 않는다(반대 방향 `admin → account` 가 이미 있어 순환). 수락이 계정 판정을 필요로 하면 admin-service 가 account-service 에 묻는다([admin-to-account.md](./admin-to-account.md)).

---

## GET /internal/operator-assignments/check

선택된 customer tenant 에 대한 운영자의 **effective tenant scope** 포함 여부를 확인한다. auth-service 가 assume-tenant 토큰을 발급하기 전 fail-closed 게이트로 호출한다.

> **TASK-BE-338 (ADR-MONO-020 D3 amendment 2026-06-05)** — 응답에 `orgScope` 필드가 **additive** 로 추가되었다(선택 assignment 의 per-assignment 데이터-스코프). 기존 `assigned` 필드·판정 규칙·상태코드는 **byte-불변**. auth-service `TenantClaimTokenCustomizer.customizeForAssumeTenant` 가 이 값을 assume-tenant 토큰 `org_scope` claim 으로 주입한다(하드코딩 `["*"]` BE-337 브리지 대체; `null`/empty → `["*"]` = net-zero). 오직 erp 가 subtree-root 를 소비(TASK-ERP-BE-008).

> **TASK-MONO-299 (ADR-MONO-040 Phase 3 part B 2026-06-18)** — 운영자 row 조회는 **account_id 단독**이다. Phase 2 의 임시 **DUAL-KEY** email fallback 과 `X-Subject-Email` 헤더는 **제거**되었다. part A(TASK-MONO-298)가 `admin_operators.oidc_subject` 를 account_id 로 backfill 했으므로 admin-service 는 `oidcSubject`(=account_id, SAS `sub`)로 직접 조회한다. (실배포 전제: part-A backfill 이 선행돼야 한다 — 미migrate 운영자는 fallback 제거 후 fail-closed 된다.)

> **TASK-MONO-771 (ADR-MONO-080 D4 · R2, 소유자 결정 OD-2 · OD-3)** — 응답에 `mfaRequired` 필드가 **additive** 로 추가된다(아래 표 · 판정 규칙 6). 기존 `assigned` · `orgScope` · `delegatedScope` 의 판정과 상태코드는 불변이다. 요구 여부만 admin-service 가 계산하고, subject `amr` 과의 비교는 auth-service 가 한다([auth-api.md § Assume-Tenant Exchange](../auth-api.md) 2단계 게이트). 같은 요청 안의 admin-service **로컬** 읽기(`tenant_entry_policy` · `admin_roles.require_2fa`)라 새 서비스 간 호출이 없다.

**Query Parameters**:

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `oidcSubject` | string (UUID) | Yes | 운영자의 IAM OIDC subject (`sub` = account_id, ADR-040 Phase 2). `admin_operators.oidc_subject` 로 운영자 row 를 fail-closed 조회(account_id 단독 키, TASK-MONO-299) |
| `tenantId` | string (slug) | Yes | 선택된(assume 대상) customer tenant id |

**Response 200**:
```json
{
  "assigned": true,
  "orgScope": ["dept-sales"],
  "mfaRequired": true
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
| `mfaRequired` | boolean | **TASK-MONO-771 — additive, 200 응답에 항상 실린다**(`true`/`false`, 생략 없음). `assigned=true` 일 때 = **선택 테넌트의 진입 정책**(`tenant_entry_policy.require_mfa`, 행 없음 = `false`) **∨ 운영자가 `require_2fa=TRUE` 역할을 하나라도 보유**(`anyRoleRequires2fa` — OD-2: 역할 플래그는 assume 에서도 문다). `assigned=false` 면 `false`(판정할 것이 없다 — 어차피 거절). 🔴 **auth-service 는 이 필드의 부재를 `true` 로 읽는다**(fail-closed — 구버전 admin 과의 어긋남이 2단계 없는 발급으로 새지 않게. 2단계를 거친 subject 는 그래도 통과한다). 판정 규칙 6 |
| `delegatedScope` | object `{domains:[], roles:[]}` \| absent | **TASK-BE-477 (ADR-MONO-045 D3/D5) — additive.** 오직 **파트너십-파생 host reach** 케이스에만 존재하는 cross-org confinement 블록: partner 테넌트 B 의 participant 인 운영자가 host 테넌트 A(=`tenantId`)를 assume 할 때 얻는 **capped** 도메인-운영 스코프 = `delegated_scope ∩ participant_scope ∩ host-holds`. **정상(비-파트너십) assignment 와 모든 `assigned=false` 케이스에는 이 필드가 부재**한다(`@JsonInclude(NON_NULL)` 로 omit — 기존 `{assigned, orgScope}` shape 은 byte-불변). auth-service(step 2b)가 이 값으로 assume-tenant 토큰의 `entitled_domains` 를 `domains` 와 교집합하고 role 을 `roles` 로 캡한다. **admin scope 는 절대 확장되지 않는다** — cross-org actor 는 host 에서 `effectiveAdminScope` 공집합(→ `/api/admin/**` 403). 이 필드가 실린다고 해서 운영자가 host 를 administer 할 수 있는 것은 아니다. |

**판정 규칙** (server-side, admin-service):

1. **account_id 조회 (TASK-MONO-299)**: `oidcSubject`(account_id) 로 `admin_operators` row 조회 (`AdminOperatorPort.findByOidcSubject`). miss OR 비-`ACTIVE` → `assigned=false`, `orgScope=null` (fail-closed; 운영자 존재 여부를 boolean 너머로 노출하지 않는다).
2. platform-scope 운영자 (`tenant_id == '*'`, `isPlatformScope()`) → 비어있지 않은 모든 `tenantId` 에 대해 `assigned=true` (sentinel 이 모든 tenant 를 부여; assumed 토큰은 여전히 선택된 구체 `tenant_id` 를 운반한다 — `'*'` 토큰은 절대 발급되지 않는다). 명시적 assignment row 가 없으므로 `orgScope=null` (→ `["*"]`).
3. 그 외 → `assigned = TenantScopeResolver.resolveEffectiveTenantScope(internalId, homeTenant).contains(tenantId)` (D1 assignment rows ∪ {legacy home tenant} — BE-326 dual-read). `assigned=true` 시 `orgScope = operator_tenant_assignment.{(operatorInternalId, tenantId)}.org_scope` (해당 (운영자, 선택 테넌트) assignment row 의 `org_scope`; row 부재 또는 컬럼 NULL → `null`).
4. `tenantId` blank → `assigned=false`, `orgScope=null`.
0. **`admin_operators.confined_tenant_id` — 한 테넌트로 묶인 운영자 (`TASK-MONO-751`, 2026-10-03 소유자 결정 «데모 운영자는 팬 전용으로»).** 운영자 행을 찾고 ACTIVE 를 확인한 직후, **2번(platform-scope)보다 먼저**: 이 컬럼이 비-NULL 이고 `tenantId` 와 다르면 `assigned=false`. 같으면 나머지 규칙이 그대로 판정한다(좁히기만 — 열지 않는다). `NULL`(기존 모든 운영자)은 이 규칙에 걸리지 않는다. 쓰는 곳: 데모 플랫폼 운영자(`'*'`, `confined_tenant_id='fan-platform'`) — `fan-platform` 만 assume 가능.
5. **`tenantId == fan-platform` — 플랫폼 운영자 전용 (`TASK-MONO-750`, `ADR-MONO-079` D4-A 라이더 R3).** 2번(platform-scope)에 걸리지 않은 운영자 — 즉 **고객사 운영자** — 는 `fan-platform` 에 대해 **항상 `assigned=false`** 다. assignment row 가 있어도(이 surface 가 생기기 전에 만들어졌든 직접 SQL 이든), 파트너십-파생 host reach(3·`delegatedScope`) 가 있어도 같다 — 이 판정은 3번과 파트너십 분기 **앞에서** 내려진다. 이유: `fan-platform` 이 `fan` 도메인을 구독하므로 이 테넌트를 assume 한 토큰은 `FAN_OPERATOR` 를 파생받고, 그 역할로 열리는 길(artist-service 디렉터리 관리)은 플랫폼 운영자 몫으로만 열렸다. 플랫폼 운영자는 2번으로 지금처럼 `assigned=true`.
6. **`mfaRequired` — 2단계 요구 (`TASK-MONO-771`, `ADR-MONO-080` D4 · R2, 소유자 결정 OD-2).** 위 규칙들이 `assigned=true` 를 낸 **뒤에** 계산한다(`assigned` 판정을 바꾸지 않는다): `mfaRequired = tenant_entry_policy(tenantId).require_mfa ∨ anyRoleRequires2fa(운영자)`. 정책 행이 없으면 `false`(꺼짐). **경로 불문** — 2(플랫폼 `'*'`) · 3(assignment) · 파트너십 host reach 어느 길로 `assigned=true` 가 나와도 정책은 «들어가는 테넌트» 의 것이다. `assigned=false` 는 언제나 `mfaRequired=false`. 정책 · 역할 읽기 실패는 이 엔드포인트의 5xx 다(→ auth-service fail-closed, 아래 Caller Constraints) — `false` 로 메우지 않는다.

**Side Effect**: 없음 (read-only — `admin_actions` row 미기록).

**Errors**:

| Status | 조건 |
|---|---|
| 401 `UNAUTHORIZED` | IAM client_credentials JWT 미제시/무효 (`/internal/**` 체인 fail-closed) |
| 400 `VALIDATION_ERROR` | `oidcSubject`/`tenantId` 파라미터 누락 |

운영자 미존재/비-ACTIVE/미할당은 모두 `200 {assigned:false}` 로 응답한다 (열거 방어; 별도 4xx 로 구분하지 않는다). 이 경우 `mfaRequired` 는 `false` 다 — 2단계 요구 여부가 운영자 존재를 드러내지 않게.

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
| `axes` | string (optional) | No | **TASK-MONO-772 (S6) — additive.** 물을 축: `OIDC_SUBJECT` · `IDENTITY` 의 쉼표 목록. **생략 = 둘 다**(지금 동작 · byte-불변). 모르는 값 → `400 VALIDATION_ERROR` |

**Response 200**:
```json
{ "operatorFaceted": true }
```

**판정 (read-only)**: `admin_operators` 행이 **하나라도**(상태 무관 — `SUSPENDED` 운영자도 측면이다) 다음 중 **물은 축** 하나를 만족하면 `true`:

1. (`OIDC_SUBJECT`) `oidc_subject = accountId` — ADR-MONO-044 D5 셀프 온보딩 운영자(운영자 `sub` = 그 소비자 계정 id).
2. (`IDENTITY`) `identityId` 가 주어졌고 `identity_id = identityId` — 운영자 신원 연결(ADR-MONO-034 U3, `LinkOperatorIdentityUseCase`). 신원 행을 풀로 옮기면 그 운영자의 신원이
   풀 신원이 되므로 운영자 측면이다.

그 밖 `false`. 운영자 존재를 boolean 너머로 드러내지 않는다(어느 축이 맞았는지 답하지 않는다 — 호출자가 축을 골라 묻는다).

> **TASK-MONO-772 (ADR-MONO-080 D6 넷째 줄 · 구현자 결정 D-6) — 이동기는 S6 부터 `axes=IDENTITY` 로만 묻는다.** 셀프 온보딩 운영자(`oidc_subject` 축)는 이제 **같은 id 로** 풀로 옮긴다 —
> 계정 id 가 그대로라 `admin_operators.oidc_subject` 를 다시 쓸 것이 없고(쓰기 0), 같은 사람이 같은 `sub` 로 콘솔에 들어온다(772 AC-5 — 풀 세션의 콘솔 토큰은
> [auth-api.md § 풀 계정의 콘솔 토큰](../auth-api.md#풀-계정의-콘솔-토큰--운영자-측면이-있을-때만-task-mono-772--adr-mono-080-d6), 🔴 그 절의 구현(S4)이 S6 보다 **먼저** 머지돼야 한다 — 옮긴 순간 그 사람의 세션은 풀 principal 이다).
> 신원 축(소비자 사이트 테넌트에 334 로 만들어 신원이 연결된 운영자 — 정적 0)은 계속 건너뛴다(772 AC-0 F11). 이 엔드포인트의 판정 자체는 바뀌지 않는다 — 바뀌는 것은 호출자가 고르는 축이다.

**Side Effect**: 없음 (read-only — `admin_actions` row 미기록).

**Errors**:

| Status | 조건 |
|---|---|
| 401 `UNAUTHORIZED` | IAM client_credentials JWT 미제시/무효 (`/internal/**` 체인 fail-closed) |
| 400 `VALIDATION_ERROR` | `accountId` 누락 |

**Caller (auth-service) — fail-CLOSED**: 아래 Caller Constraints 와 같은 타임아웃·재시도·circuit breaker(별도 breaker 이름 — 이동 배치의 실패가 assume-tenant 게이트를
열지 않는다). 답을 못 받으면(4xx · 5xx · 타임아웃 · circuit-open · 본문 이상) **옮기지 않는다** → `503 SERVICE_UNAVAILABLE`. 모른 채 옮기면 운영자 계정을 풀로 끌고 간다.

---

## GET /internal/operators/console-eligibility — 풀 계정에 콘솔 토큰을 줄까 (TASK-MONO-772)

**ADR-MONO-080 D6 셋째 줄 · 구현자 결정 D-5 · `TASK-BE-615` D-5 개정.** 풀 principal 이 콘솔 client(`platform-console-web`)로 토큰을 받으려 할 때 auth-service 발급자가
«이 계정에 **살아 있는** 운영자 측면이 있나» 를 묻는다. 있으면 콘솔 토큰을 내고, 없으면 지금처럼 거절한다([auth-api.md § 풀 계정의 콘솔 토큰](../auth-api.md#풀-계정의-콘솔-토큰--운영자-측면이-있을-때만-task-mono-772--adr-mono-080-d6)).

**Query Parameters**:

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `accountId` | string (UUID) | Yes | 풀 principal 의 계정 id(= 콘솔 토큰에 실릴 `sub`) |

**Response 200 — 항상 200** («아니오» 도 본문의 답이다):
```json
{ "eligible": true }
```

**판정 (read-only)** — **토큰 교환과 같은 술어**(`TokenExchangeService` — `admin_operators.oidc_subject = accountId` ∧ `status = ACTIVE`). 그 행이 있으면 `true`, 없거나 비-`ACTIVE` 면 `false`.

- 🔴 **`GET /internal/operators/facet` 를 재사용하지 않는다** — 그쪽은 «옮겨도 되나» 를 묻는 **상태 무관 · 두 축** 술어다. 그것으로 콘솔 토큰을 주면 퇴사(`SUSPENDED`) · 정지된 운영자가 콘솔 토큰을 받고, 그 다음 교환이 `401` → 콘솔 `/onboarding` 으로 간다(772 AC-3 위반). 질문이 다르면 술어도 다르다.
- 신원 축(`identity_id`)은 묻지 않는다 — 교환이 `sub` 로만 해석하므로, 신원만 연결된 운영자에게 콘솔 토큰을 주어도 교환이 그 사람을 찾지 못한다.
- `confined_tenant_id` · 진입 정책 · 2단계는 여기서 보지 않는다 — 그것들은 교환 · assume 이 문다(TASK-MONO-751 · 771). 이 읽기는 «콘솔 셸에 들어올 운영자인가» 하나다.
- 운영자 존재를 boolean 너머로 드러내지 않는다. 감사 행 · 이벤트 · 변이 없음.

**Errors**:

| Status | 조건 |
|---|---|
| 401 `UNAUTHORIZED` | IAM client_credentials JWT 미제시/무효 (`/internal/**` 체인 fail-closed) |
| 400 `VALIDATION_ERROR` | `accountId` 누락 · 공백 |

**Caller (auth-service) — fail-CLOSED**: 아래 Caller Constraints 와 같은 타임아웃 · 재시도 · circuit breaker(**별도 breaker 이름** — 콘솔 로그인 장애가 assume-tenant 게이트 · 이동기 breaker 를 열지 않는다). 답을 못 받으면(4xx · 5xx · 타임아웃 · circuit-open · `eligible` 없는 200) **콘솔 토큰을 내지 않는다** — `invalid_grant` + 고정 상수 `error_description=operator_eligibility_unavailable`(«측면 없음» 의 거절과 **다른** 값 — 장애를 «다른 계정으로 로그인돼 있다» 로 보이게 하지 않는다, 772 AC-0 F5). 호출 시점: 풀 principal 의 콘솔 `authorization_code` · `refresh_token` 발급 **매번**(캐시 없음 — 멤버십 읽기와 같은 모양, 퇴사 · 정지가 다음 refresh 부터 걸린다).

---

## POST /internal/operator-invitations/preview — 초대 미리보기 (TASK-MONO-772)

IdP 수락 화면이 «무엇을 수락하는지» 를 그리기 위한 읽기([auth-api.md § IdP 브라우저 화면 — 운영자 초대 수락](../auth-api.md#idp-브라우저-화면--운영자-초대-수락-task-mono-772--adr-mono-080-d6)).
토큰을 쿼리 문자열에 싣지 않으려고 `POST` 다(접근 로그 · 프록시 로그에 남지 않게). **아무것도 쓰지 않는다**.

**Request**:
```json
{ "token": "string (초대 메일 링크의 토큰)" }
```

**Response 200**:
```json
{
  "tenantId": "acme-corp",
  "tenantDisplayName": "Acme Corp",
  "maskedEmail": "p*****@example.com",
  "roles": ["SUPPORT_LOCK"],
  "status": "PENDING",
  "expired": false,
  "expiresAt": "2026-10-17T10:00:00Z"
}
```

- 토큰의 SHA-256 으로 찾는다. 없음 · `CANCELLED` → `404 OPERATOR_INVITATION_NOT_FOUND`(둘을 구별하지 않는다 — 열거 방지).
- `maskedEmail` — 초대 주소를 마스킹한 것(R4). 수락 화면이 «이 주소를 인증한 계정으로 로그인하세요» 를 말하는 데 쓴다. 원문 주소는 싣지 않는다.
- `tenantDisplayName` — account-service 테넌트 읽기에서. 실패하면 `null`(화면은 `tenantId` 를 쓴다) — 미리보기는 판정이 아니라 fail-soft 다.
- `ACCEPTED` · 만료도 200 으로 답한다(`status` · `expired`) — 화면이 «이미 수락됨» · «만료됨 — 초대한 분께 다시 보내 달라고 하세요» 를 고른다.
- 감사 행 · 이벤트 · 변이 없음. 로그에 토큰을 쓰지 않는다.

**Errors**: `401 UNAUTHORIZED` · `400 VALIDATION_ERROR`(`token` 누락) · `404 OPERATOR_INVITATION_NOT_FOUND`.

---

## POST /internal/operator-invitations/accept — 초대 수락 (TASK-MONO-772)

**ADR-MONO-080 D6 첫 줄 · 라이더 R4 · 구현자 결정 D-3 · 소유자 결정 OD-1.** IdP 수락 화면(`POST /operator-invitations/accept`)이 **로그인한 풀 계정**을 대신해 부른다.
관리 표면: [admin-api.md § Operator Invitation](../admin-api.md#operator-invitation-task-mono-772).

**Request**:
```json
{ "token": "string", "accountId": "string (UUID)" }
```

| 필드 | 설명 |
|---|---|
| `token` | 초대 메일 링크의 토큰(수락 화면 폼의 hidden 필드 — 그 페이지의 주인에게서 온 값) |
| `accountId` | 🔴 **IdP 세션 principal 의 계정 id** — auth-service 가 세션에서 꺼낸다. 화면의 폼 값 · 요청 파라미터에서 받지 않는다. 이 값이 «로그인한 상태로» 의 증거다 |

**Response 200**:
```json
{
  "operatorId": "string (UUID v7)",
  "tenantId": "acme-corp",
  "roles": ["SUPPORT_LOCK"],
  "alreadyAccepted": false
}
```

`alreadyAccepted = true` = **같은 계정**이 이미 이 초대를 수락했다(재제출 · 뒤로가기 재전송) — 첫 결과를 그대로 돌려준다(셀러 구성원 초대와 같은 규칙).

**판정 순서** — 처음 실패한 것이 답하고, 🔴 **거절은 아무것도 쓰지 않는다**(초대는 `PENDING` 그대로 — 맞는 사람이 나중에 수락할 수 있다):

| # | 판정 | 실패 시 |
|---|---|---|
| 1 | 토큰의 SHA-256 으로 초대를 찾는다 | 없음 · `CANCELLED` · **재발송으로 덮인 옛 토큰** → `404 OPERATOR_INVITATION_NOT_FOUND`(구별하지 않는다 — 열거 방지) |
| 2 | 이미 `ACCEPTED` 인가 | 수락한 계정 = `accountId` → **`200` + 첫 결과**(`alreadyAccepted=true`, 아래 판정을 다시 하지 않는다) · 다른 계정 → `409 OPERATOR_INVITATION_ALREADY_USED` |
| 3 | 만료(`expires_at ≤ 지금`) | `410 OPERATOR_INVITATION_EXPIRED` |
| 4 | 🔴 **account-service 판정** — [`POST /internal/accounts/{accountId}/verified-email:match`](./admin-to-account.md#post-internalaccountsaccountidverified-emailmatch--인증된-이메일-일치-판정-task-mono-772) (`expectedEmail` = 초대 이메일). 그 안의 순서: 풀 계정인가 → 이메일 = 초대 이메일 → 이메일 **인증됨**(`VerifiedEmailRequirement` — `TASK-MONO-770` 의 공용 술어를 그 자리에서 부른다) | 계정 없음 · 풀 계정 아님(사이트 계정 · `iam` 자격 계정) → `403 OPERATOR_INVITATION_ACCOUNT_NOT_ELIGIBLE` · 이메일 불일치 → `403 OPERATOR_INVITATION_EMAIL_MISMATCH` · 미인증 → **`403 EMAIL_NOT_VERIFIED`**(770 의 공용 이름 그대로) · 판정 못 받음 → `503 DOWNSTREAM_ERROR` / `CIRCUIT_OPEN` (**fail-closed** — 모르고 붙이지 않는다) |
| 5 | 근거가 아직 살아 있는가 — 초대 테넌트 `ACTIVE`(account-service 테넌트 읽기) · 초대자(`invited_by`) `ACTIVE` · 초대 테넌트가 **지금도** 초대자의 admin-grant 범위 안(D2) · 초대 역할이 **지금도** 초대자의 부여 메뉴 안(D3 — 7일 사이 권한이 줄었을 수 있다) | 하나라도 아니면 `409 OPERATOR_INVITATION_INVALIDATED` · 테넌트 읽기 실패 → `503` |
| 6 | 🔴 **OD-1 — 한 사람 = 한 회사**: `admin_operators.oidc_subject = accountId` 인 행이 **없다**(상태 무관 — 정지된 측면도 측면이다) | `409 OPERATOR_ALREADY_PROVISIONED` |
| 7 | 그 테넌트에 같은 이메일의 운영자 행이 없다(`(tenant_id, email)` — 초대 발급 뒤 다른 길로 생겼을 수 있다) | `409 OPERATOR_EMAIL_CONFLICT` |
| 8 | 한 admin 트랜잭션(아래) — 초대의 조건부 갱신 `PENDING → ACCEPTED`(`WHERE id = ? AND status = 'PENDING' AND token_hash = ?`) | 영향 행 0(동시 수락 · 취소 · 재발송이 먼저 이겼다) → 다시 읽어 2번 규칙으로 답한다(같은 계정 → `200`, 그 밖 → `409 OPERATOR_INVITATION_ALREADY_USED` · 취소 · 재발송이었으면 `404`). `oidc_subject` · `(tenant_id, email)` UNIQUE 위반(6 · 7 의 경합) → 각 `409` |

- 4 의 순서(이메일 불일치가 미인증보다 먼저)는 `TASK-MONO-770` 이 셀러 구성원 수락에서 고른 순서와 같다 — 틀린 주소가 더 구체적인 답이다.
- 2 를 3 보다 먼저 보는 이유: 수락이 끝난 초대가 나중에 만료 시각을 지나도 같은 사람의 재제출은 성공으로 답해야 한다(셀러 구성원 초대와 같은 순서). 772 AC-0 D-3 는 만료를 먼저 적었으나 결과는 같다 — 다른 계정은 어느 순서든 `409`/`410` 중 하나로 거절된다(S1 기록).
- `amr ∋ mfa` 를 요구하지 않는다(D-3) — 측면을 **만드는** 일이고 진입 조건은 교환 · assume 이 문다.

**8 의 트랜잭션이 쓰는 것**:

| 행 | 값 |
|---|---|
| `admin_operators` 새 행 | `operator_id` = 새 UUID v7 · `tenant_id` = 초대 테넌트(홈 = 회사 — ADR-080 D5) · `email` = 초대 이메일 · `display_name` = 초대의 `displayName` · `password_hash` = **NULL**(OIDC 전용 — break-glass 없음) · 🔴 **`oidc_subject` = `accountId`**(772 AC-0 F1 — 이것이 «로그인 문»: 토큰 교환이 이 값으로 운영자를 찾는다) · `status = ACTIVE` |
| 역할 grant | 초대의 `roles` 각각, grant 테넌트 = 초대 테넌트(ADR-024 D2 — `POST /api/admin/operators` 가 쓰는 grant 행과 같은 모양) |
| 테넌트 배정 | 초대 테넌트 전체(`org_scope = NULL` ⟺ `["*"]`) — 홈 테넌트 운영자의 기존 모양 |
| `operator_invitation` | `status = ACCEPTED` · `accepted_at` · `accepted_account_id = accountId` · `accepted_operator_id` |
| `admin_actions` | `action_code = OPERATOR_INVITATION_ACCEPT` · **`operator_id` = 새 운영자**(수락하는 사람은 그 전엔 운영자가 아니다 — 감사 주체 = 이제 생긴 그 운영자) · `permission_used = <self_invitation_accept>`(자기 흐름 상수 — `<self_enrollment>` 와 같은 부류, 권한 키 평가가 아니다) · `reason = <self_invitation_accept>` · `target_type = OPERATOR_INVITATION` · `target_id = <invitationId>` · `target_tenant_id = 초대 테넌트` · `downstream_detail = "accountId=<accountId>"`(772 AC-0 F19 — `TASK-MONO-774` 가 «초대 수락의 부산물» 로 직원 연결을 고를 때 읽을 `(accountId, tenantId, operatorId)` 가 이 한 행에 모인다) + outbox |

- 중앙 신원 링크(ADR-MONO-034 U3)는 `FirstAdminProvisioner` 처럼 **fail-soft** 로 트랜잭션 밖에서 시도한다 — 실패해도 수락은 성공이고 운영자는 unlinked 로 남는다(나중에 `identity:link`).
- 🔵 **거절은 감사 원장(`admin_actions`)에 남지 않는다** — 그 행은 운영자를 주체로 요구하는데(`operator_id NOT NULL`) 거절된 수락자에게는 운영자 행이 없다. 거절은 구조화 로그 한 줄(초대 id · 결과 코드 · 계정 id — 토큰 · 이메일 없음)이다.

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `UNAUTHORIZED` | IAM client_credentials JWT 미제시/무효 |
| 400 | `VALIDATION_ERROR` | `token` · `accountId` 누락 |
| 403 | `OPERATOR_INVITATION_ACCOUNT_NOT_ELIGIBLE` | 계정 없음 · 풀 계정이 아니다 |
| 403 | `OPERATOR_INVITATION_EMAIL_MISMATCH` | 로그인한 계정의 이메일 ≠ 초대 이메일 |
| 403 | `EMAIL_NOT_VERIFIED` | 이메일은 맞지만 인증되지 않았다 — 인증 뒤 같은 초대로 다시 된다 |
| 404 | `OPERATOR_INVITATION_NOT_FOUND` | 토큰에 맞는 초대 없음 · 취소됨 · 재발송으로 죽은 토큰 |
| 409 | `OPERATOR_INVITATION_ALREADY_USED` | 다른 계정이 이미 수락했다 |
| 409 | `OPERATOR_INVITATION_INVALIDATED` | 테넌트 비활성 · 초대자 비활성 · 초대자의 범위 · 부여 메뉴가 더는 이 초대를 덮지 않는다 — 초대한 쪽이 다시 보내야 한다 |
| 409 | `OPERATOR_ALREADY_PROVISIONED` | 이 계정에 이미 운영자 측면이 있다(OD-1 — 772 는 한 사람 = 한 회사) |
| 409 | `OPERATOR_EMAIL_CONFLICT` | 그 테넌트에 같은 이메일의 운영자가 이미 있다 |
| 410 | `OPERATOR_INVITATION_EXPIRED` | 만료 — 초대한 쪽의 재발송이 새 링크를 보낸다 |
| 503 | `DOWNSTREAM_ERROR` / `CIRCUIT_OPEN` | account-service 판정 · 테넌트 읽기 실패 — 아무것도 쓰지 않음 |

**Caller (auth-service)**: 아래 Caller Constraints 의 타임아웃 · circuit breaker(별도 breaker 이름). 🔴 **재시도하지 않는다** — 쓰기다(4xx 는 원래 재시도 금지, 5xx · 타임아웃도 화면의 «다시 시도» 버튼에 맡긴다; 같은 계정의 재제출은 2번 규칙이 멱등으로 답한다).
응답 코드 → 화면 문구 매핑은 [auth-api.md § IdP 브라우저 화면 — 운영자 초대 수락](../auth-api.md#idp-브라우저-화면--운영자-초대-수락-task-mono-772--adr-mono-080-d6) 이 정본이다.

---

## Caller Constraints (auth-service 측 — **fail-CLOSED**)

- 타임아웃: 연결 3s, 읽기 5s (account edge 와 동일 정책)
- 재시도: 2회 (지수 백오프 + jitter). 4xx 는 재시도 금지
- Circuit breaker: 실패율 50% / 10초 sliding window → open → half-open
- **⚠️ fail-CLOSED**: admin-service 장애 시(`assigned=false` / 4xx / 5xx / circuit-open / timeout / IO 모두) **assume-tenant 발급 거부** (`AssumeTenantDeniedException` → RFC 8693 `invalid_grant` / 400, 토큰 미발급). **`assigned=true` 여도 `mfaRequired` 가 `true` 이거나 부재이고 subject `amr` 에 `mfa` 가 없으면 거부** — `invalid_grant` + `error_description=insufficient_user_authentication`(TASK-MONO-771; 장애 거부와 **다른** 고정 상수 — 장애를 «2단계 필요» 로 보이게 하지 않는다). 이 게이트는 **절대 fail-soft 하지 않는다** — account-service `entitled_domains` 도출(fail-soft)과 정반대 정책이다. 인가 게이트이므로 가용성에 의존해 토큰을 발급해서는 안 된다 (격리 위반 = isolation breach).
- **감사 기록 없음**: read 이므로 "audit first" 가 적용되지 않는다 (admin-to-account 의 lock/unlock 명령과 다름).
