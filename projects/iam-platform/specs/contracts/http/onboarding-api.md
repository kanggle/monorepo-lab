# HTTP Contract: admin-service — Self-Service Tenant Onboarding

TASK-BE-474 / [ADR-MONO-044](../../../../../docs/adr/ADR-MONO-044-self-service-tenant-onboarding.md) (ACCEPTED).

Self-service B2B tenant onboarding: an authenticated visitor creates a NEW tenant and is
appointed its first `TENANT_ADMIN` + `TENANT_BILLING_ADMIN`, with **no platform `SUPER_ADMIN`
in the loop** (AWS "create account → root" / GCP "create project → owner" parity). Everything
downstream (managing the tenant's operators/subscriptions) is [ADR-MONO-024](../../../../../docs/adr/ADR-MONO-024-tenant-admin-delegation.md).

Base path: `/api/admin/onboarding`. All endpoints route via the IAM gateway.

> 🔴 **TASK-MONO-773 (ADR-MONO-080 D9 = T1 · 소유자 결정 OD-1 ~ OD-8, 2026-10-15 UTC — 계약 S1, 구현 S3 · S5 · S6)** — 콘솔의 «테넌트 생성» 은 **하나**다. 이 엔드포인트가 **비-`SUPER_ADMIN` 경로 전부**다: ① 운영자 측면이 없는 개인(풀) 계정(콘솔 «비운영자 셸» — [auth-api.md § 풀 계정의 콘솔 토큰](auth-api.md#풀-계정의-콘솔-토큰--운영자-측면이-있을-때만-task-mono-772--adr-mono-080-d6) 이 773 S5 부터 그 토큰을 준다) ② 이미 회사 운영자인 사람의 **두 번째 회사**(ADR-MONO-082 D6 b — 아래 «다회사 운영자와 온보딩» 5행, 새 경로를 만들지 않는다). `SUPER_ADMIN` 의 생성은 여기가 아니라 [admin-api.md § POST /api/admin/tenants](admin-api.md#post-apiadmintenants)(첫 관리자 = 지정한 이메일에 초대 — 테넌트는 `PENDING`).
> 773 이 이 엔드포인트에 더하는 것은 넷이다 — **트러스트 게이트**(인증된 이메일 · 게이트웨이 속도 제한 · 신원당 상한 3 — ADR-044 D4) · **회사 생성자의 업무 권한 세트**(`all-domains-full` × 출처 `ORIGIN` — ADR-083 OD-5) · **상한 경합 닫기**(운영자 행 잠금 + 재셈) · **«받은 초대» 읽기**(아래 § POST …/received-invitations). 그 밖의 판정 · 응답 모양은 그대로다.
> 🔴 **머지 순서**: 이 문서의 트러스트 게이트(S3)는 auth-service 의 비운영자 셸 토큰(S5)보다 **먼저** `main` 에 들어간다. 반대 순서면 측면 없는 풀 계정 전부가 인증 이메일 · 상한 없이 이 엔드포인트에 닿는다(773 AC-0 R3 — 772 가 측면 없는 풀 계정에 콘솔 토큰을 주지 않은 이유 그대로, 772 AC-0 F9).

---

## POST /api/admin/onboarding/organizations

Create a new tenant and become its first administrator.

**Auth required**: **Authenticated visitor, NOT an operator.** This is the one admin-service
mutation callable without an operator token. The caller presents their **own** IAM OIDC access
token (`platform-console-web` audience) in the request **body** (`subjectToken`, ADR-014
token-exchange style) — admin-service has no user-JWT header-auth surface. The endpoint is
`permitAll` at the filter layer, skipped by `OperatorAuthenticationFilter`, and validates the
token itself via `IamOidcSubjectTokenValidator` (auth-service JWKS, `iss`/`aud`/`exp`/`nbf`/RS256
+ the "no `token_type` claim" guard — operator/bootstrap tokens are rejected).

The caller's **email + display name are resolved from the AUTHORITATIVE account** (by the token's
`sub` = account_id), never trusted from the request body.

**누가 이 토큰을 들고 오나 (773 부터)** — 콘솔 client 토큰을 가진 사람 전부: 운영자(어느 회사 소속이든 · `'*'` 포함 — `'*'` 는 아래 판정 2행에서 409) · 비운영자 셸의 개인 계정(측면 없음 · 측면이 비-`ACTIVE` — OD-6). 운영자 토큰(`token_type=admin`)은 여전히 거절이다(위 «no `token_type` claim»). 콘솔은 **운영자일 때도** 이 엔드포인트를 IAM access token 으로 부른다(운영자 토큰이 아니다 — 생성자 신원은 `sub` 이고, 운영자 토큰에는 계정 `sub` 가 없다).

**Rate limit (OD-8)**: 게이트웨이 scope **`onboarding`** — IP 당 **5회 / 60초**(`signup` 과 같은 모양, [gateway-api.md](gateway-api.md) · [redis-keys.md](../../services/gateway-service/redis-keys.md)). 넘으면 게이트웨이가 `429 RATE_LIMITED` + `Retry-After` 로 답하고 admin-service 에 닿지 않는다. 🔵 ADR-044 D4 의 «per-identity» 반쪽은 별도 카운터가 아니라 **신원당 소유 상한**(아래, 3)으로 충족한다 — 한 신원이 만들 수 있는 회사의 **총량**을 상한이 묶고, 새 계정을 찍어 내는 스팸은 «인증된 이메일» + per-IP 가 문다(소유자 결정 OD-8). 이 scope 는 이 `POST …/organizations` 하나에만 건다(아래 «받은 초대» 읽기에는 걸지 않는다 — 화면 진입마다 부르는 읽기다). ⚪ 게이트웨이를 거치지 않는 토폴로지(CI 의 콘솔 → admin-service 직접)에는 이 제한이 없다 — 그 경로의 보호는 이메일 게이트 · 상한이다. Redis 장애 시 게이트웨이 rate limit 은 fail-open(redis-keys.md § Failure Mode)이고, 이메일 게이트 · 상한은 fail-closed 다.

**Request**:
```json
{
  "subjectToken": "string (required — the caller's IAM OIDC access token)",
  "tenantId": "string (required — new tenant slug, ^[a-z][a-z0-9-]{1,31}$)",
  "organizationName": "string (required, max 100 — tenant display name)"
}
```

**Response 201**:
```json
{
  "tenantId": "acme-corp",
  "operatorId": "string (UUIDv7 — the minted first-admin operator)",
  "roles": ["TENANT_ADMIN", "TENANT_BILLING_ADMIN"],
  "status": "ACTIVE"
}
```

**What it provisions (atomically, ADR-044 D1)**:
1. A new `tenants` row in account-service (`tenantType=B2B_ENTERPRISE`, status ACTIVE).
2. The caller's central identity resolved-or-created (born-unified, `reuseExisting=true`,
   ADR-036 — a prior consumer converges on the same identity; fail-soft).
3. A backing operator (home `tenant_id` = the new tenant, OIDC-only, no password) granted
   **both** `TENANT_ADMIN` **and** `TENANT_BILLING_ADMIN` **scoped to the new tenant** (D6 — the
   owner can both administer AND self-enable domain subscriptions).
4. A whole-tenant `operator_tenant_assignment` so the owner can assume-tenant into it.
5. **(773 S3 — ADR-MONO-083 OD-5 · 773 OD-4)** one `operator_domain_set_grant` row: (the owner's operator row, **the new tenant**, preset **`all-domains-full`**, source **`ORIGIN`**, `granted_by = NULL`) — in the **same admin transaction** as 3 · 4, on **both** branches (new operator row · membership added to an existing row). See «회사 생성자의 업무 권한 세트» below.

**Security invariants (ADR-044 D2 — the safety keystone)**:
- The self-grant's `tenant_id` is **always the just-created tenant** — never `'*'`, never an
  existing tenant. The role rows are structurally incapable of targeting any other boundary.
- `SUPER_ADMIN` is net-zero; multi-tenant M1-M7 row-isolation is untouched; ADR-024 D2/D3
  no-escalation governs everything the new admin then does.

**Entitlement at birth (ADR-044 D6)**: the new tenant is born with **zero** `tenant_domain_subscription`
rows — the owner self-enables domains afterward via their `TENANT_BILLING_ADMIN`
(`subscription.manage`) surface. The grant is a *capability to subscribe*, not a subscription.

**Failure / compensation (ADR-044 D3 — fail-closed)**: a tenant with no administrator is a dead,
unreachable boundary. If first-admin provisioning fails after the tenant was created, the tenant
is **compensated by SUSPEND** (there is no tenant hard-delete; `SUSPENDED` freezes logins/signups)
and the error is rethrown — no half-provisioned ACTIVE tenant lingers.

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | `tenantId` 슬러그 형식 불일치 / `organizationName` 누락·초과 |
| 401 | `TOKEN_INVALID` (or `UNAUTHORIZED`) | `subjectToken` 서명/iss/aud/exp 실패, `token_type` 클레임 보유(operator/bootstrap 토큰), `sub` 부재, JWKS 도달 실패 (fail-closed) |
| 403 | `EMAIL_NOT_VERIFIED` | **TASK-MONO-773 (ADR-044 D4 — 구현 S3)** — 호출자 계정의 이메일이 인증되지 않았다. **테넌트를 만들기 전에** 판정 — 아무것도 생성 안 됨. 아래 «트러스트 게이트» |
| 403 | `ONBOARDING_ACCOUNT_NOT_ELIGIBLE` | **TASK-MONO-773 (구현 S3)** — 호출자가 아래 «트러스트 게이트» 의 두 자격 어디에도 들지 않는다: 이메일을 판정할 수 있는 `ACTIVE` 개인(풀) 계정이 아니고(사이트 계정 · B2B 계정 · 비-`ACTIVE` 풀 계정 — account-service 계정 행은 있다), `iam` 자격 운영자(계정 행 없음 ∧ 운영자 측면 있음)도 아니다(계정 행도 측면도 없다). 테넌트를 만들기 전에 판정 — 아무것도 생성 안 됨 |
| 429 | `RATE_LIMITED` | **TASK-MONO-773 OD-8** — 게이트웨이 `onboarding` scope(IP 당 5회/60초) 소진. 게이트웨이가 답한다(admin-service 미도달) · `Retry-After` |
| 409 | `TENANT_ALREADY_EXISTS` | `tenantId` 슬러그 중복 (보상 불필요 — 아무것도 생성 안 됨) |
| 409 | `OPERATOR_EMAIL_CONFLICT` | (희귀) 새 테넌트에 동일 이메일 operator 존재 |
| 409 | `OPERATOR_ALREADY_PROVISIONED` (경합) | **TASK-MONO-773** — 측면 없는 같은 계정의 동시 두 요청: 둘 다 1행(새 운영자 행)을 탔고 늦은 쪽이 `oidc_subject` 플랫폼 전역 UNIQUE 에 걸렸다 → 그 쪽 테넌트는 SUSPEND 보상 후 이 코드(다시 부르면 이제 측면이 있으니 5행 «소속 추가» 를 탄다) |
| 409 | `OPERATOR_ALREADY_PROVISIONED` | **TASK-MONO-772** 로 생기고 **TASK-MONO-784 (ADR-MONO-082 b · D-12 — 구현 S5)** 로 좁혀진다 — 호출자 계정(`sub`)의 기존 운영자 측면(`admin_operators.oidc_subject = sub`)이 **새 회사의 소속을 받을 수 없다**: `'*'` 측면(ADR-082 D2 · ADR-080 D1 — 플랫폼 관리자는 회사 소속을 받지 않는다) · 비-`ACTIVE` 측면. **테넌트를 만들기 전에** 판정한다 — 아무것도 생성 안 됨, 보상 불필요. `ACTIVE` 회사 측면은 이 코드가 아니라 아래 «소속 추가» 다. (784 S5 머지 전 동작: 측면이 있으면 상태 무관 이 409 — 772 OD-1) |
| 422 | `OPERATOR_OWNED_TENANT_LIMIT_EXCEEDED` | **TASK-MONO-784 (소유자 결정 OD-1 · ADR-MONO-044 D4 · ADR-MONO-082 D4 — 구현 S5)** — 호출자의 기존 운영자 측면이 이미 신원당 상한만큼의 테넌트에서 테넌트 관리자다. **테넌트를 만들기 전에** 판정한다 — 아무것도 생성 안 됨. 🔴 **773 S3 부터** 소속 추가 트랜잭션 안에서 **한 번 더**(운영자 행 잠금 뒤) 판정한다 — 동시 요청 경합에 진 쪽은 테넌트가 만들어진 뒤 이 코드를 받고, 그 테넌트는 SUSPEND 로 보상된다. 아래 «신원당 상한» |
| 5xx | `DOWNSTREAM_ERROR` / `CIRCUIT_OPEN` | account-service 도달 실패 — 테넌트 생성 후면 SUSPEND 보상 |

**Side Effects**:
- account-service `tenants` row 생성 (+ account-side `tenant.created` 이벤트).
- admin-service `admin_operators` + `admin_operator_roles`(TENANT_ADMIN·TENANT_BILLING_ADMIN, tenant-scoped) + `operator_tenant_assignment` 생성.
  **784 S5 부터**: 호출자에게 이미 `ACTIVE` 회사 운영자 측면이 있으면 `admin_operators` 는 만들지 않고 **그 행에** 새 테넌트의 `admin_operator_roles`(TENANT_ADMIN · TENANT_BILLING_ADMIN, `tenant_id` = 새 테넌트) + `operator_tenant_assignment` 만 더한다 — 아래 «다회사 운영자와 온보딩».
- born-unified 중앙 identity resolve/create (fail-soft).
- **773 S3 부터**: `operator_domain_set_grant` 한 행(새 테넌트 × `all-domains-full` × `ORIGIN`) — 위 «What it provisions» 5.
- 🔵 `admin_actions` 감사 행 · admin outbox `tenant.created` 는 지금처럼 이 경로에 없다(관리 행위자가 없다 — 773 AC-0 § 1 · `SelfServiceOnboardingUseCase` 에 이벤트 · 감사 호출 0). 773 은 이것을 바꾸지 않는다. ⚪ 첫 블릿의 «account-side `tenant.created`» 는 773 S1 이 확인하지 않았다(account-service 는 `tenant.*` 를 내지 않는 것으로 보인다 — [tenant-events.md](../events/tenant-events.md) 발행자 = admin-service).

**Out of scope (ADR-044 deferred)**: 승인 큐(D4-C), 도메인 auto-subscribe(D6-B), org 프로필 관리, billing. ~~이메일 인증 강제(D4, 슬라이스는 인증만)~~ · ~~UI~~ — **773 이 연다**: 인증 이메일 게이트는 아래 «트러스트 게이트», UI 는 콘솔 «테넌트 생성» 화면([console-integration-contract.md § 2.6.4](../../../../platform-console/specs/contracts/console-integration-contract.md)).

**772 와 온보딩 (TASK-MONO-772 · ADR-MONO-080 D6 · D9)**:

- **누가 이 토큰을 들고 올 수 있나** — 772 부터 **운영자 측면이 있는** 풀 계정도 `platform-console-web` 토큰을 받는다([auth-api.md § 풀 계정의 콘솔 토큰](auth-api.md#풀-계정의-콘솔-토큰--운영자-측면이-있을-때만-task-mono-772--adr-mono-080-d6)). ~~측면 **없는** 풀 계정은 여전히 못 받는다 — 그래서 풀 계정의 셀프 온보딩은 772 에서도 **닫혀 있다**(이 엔드포인트에 인증 이메일 게이트가 없으므로 772 는 그 구멍을 열지 않는다, 772 AC-0 F9). 여는 것은 `TASK-MONO-773`(ADR-080 D9 = T1 — 비운영자 셸 · ADR-044 D4 트러스트 게이트)이다.~~ → **773 이 연다**(S3 게이트 → S5 셸 토큰 순서): 측면이 없거나 비-`ACTIVE` 인 풀 계정도 콘솔 **비운영자 셸 토큰**을 받고(OD-6), 그 토큰으로 이 엔드포인트에 온다. 열리는 조건이 아래 «트러스트 게이트» 다.
- 🔴 **이미 운영자 측면이 있는 호출자 (OD-1)** — 지금 코드는 테넌트를 만든 뒤 `oidc_subject` 를 쓰다 플랫폼 전역 UNIQUE 에 걸리고(`FirstAdminProvisioner` — «first-time onboarder 에게만 비어 있다»), 테넌트를 SUSPEND 로 보상한다(정지된 빈 테넌트가 남는다). 772 가 측면 있는 풀 계정에 콘솔 토큰을 주면서 이 모집단이 넓어지므로, 772 는 이것을 **테넌트 생성 전의 `409 OPERATOR_ALREADY_PROVISIONED`** 로 바꾼다(위 Errors — 초대 수락의 OD-1 판정과 같은 술어 · 같은 코드). 이미 운영자인 사람의 «두 번째 회사» 는 **`ADR-MONO-082` = b(ACCEPTED 2026-10-10 UTC) · `TASK-MONO-784`** 가 정했다 — 아래 «다회사 운영자와 온보딩». 구현 슬라이스: 772 S3(수락과 같은 판정 자리) → 784 S5(«소속 추가» 로).
- `IamOidcSubjectTokenValidator` 와 이 엔드포인트의 토큰 검증(`OnboardingController` — `amr` 을 보지 않는다)은 772 에서 바꾸지 않는다(772 AC-0 «773 을 막지 않는 조건» ⑤).

**다회사 운영자와 온보딩 (TASK-MONO-784 · ADR-MONO-082 b D2 · D4 · D6 b행 · 구현자 결정 D-12 · 소유자 결정 OD-1 — 구현 S5)**:

판정 순서 — 토큰 · 본문 검증 뒤, **테넌트를 만들기 전에** 처음 실패한 것이 답한다. 🔴 **773 S3 부터 표 앞에 트러스트 게이트(아래 0 · 0b)가 선다** — 게이트를 통과하지 못하면 측면을 보지도 않는다:

| # | 판정 | 결과 |
|---|---|---|
| (게이트웨이) | `onboarding` scope IP 당 5회/60초 | `429 RATE_LIMITED`(admin-service 미도달) |
| 0 | 🔴 **트러스트 게이트 (773 · ADR-044 D4)** — 개인(풀) 계정은 인증된 이메일 · `iam` 자격 운영자는 이메일 면제(상한 · rate limit 은 그대로) — 아래 «트러스트 게이트» | `403 ONBOARDING_ACCOUNT_NOT_ELIGIBLE` · `403 EMAIL_NOT_VERIFIED` · `503`(판정 못 받음 — fail-closed) |
| 0b | 슬러그 · 이름 형식(지금 그대로 — `400 VALIDATION_ERROR`) · 슬러그 중복은 account-service 생성이 답한다(`409 TENANT_ALREADY_EXISTS`) | — |

| # | 호출자 계정의 운영자 측면(`oidc_subject = sub`) | 결과 |
|---|---|---|
| 1 | 없다 | 지금 그대로 — 새 운영자 행(홈 = 새 테넌트) + 역할 둘 + 배정 |
| 2 | `'*'` 측면 | `409 OPERATOR_ALREADY_PROVISIONED`(ADR-082 D2 — 이 409 는 남는다) |
| 3 | 비-`ACTIVE` 측면 | `409 OPERATOR_ALREADY_PROVISIONED`(정지된 사람이 온보딩으로 측면을 되살리지 않는다) |
| 4 | `ACTIVE` 회사 측면 · **신원당 상한 도달** | `422 OPERATOR_OWNED_TENANT_LIMIT_EXCEEDED` |
| 5 | `ACTIVE` 회사 측면 · 상한 미만 | 테넌트 생성 → **그 행에 소속 추가**: 새 테넌트의 `TENANT_ADMIN` · `TENANT_BILLING_ADMIN` 역할 행(`tenant_id` = 새 테넌트) + 배정. 응답 `operatorId` = **기존** 운영자 id(`201`) |

- 🔴 **기존 회사의 역할 행 · 배정은 읽지도 고치지도 않는다**(ADR-082 D4 · ADR-044 D2 — **바이트 불변**, 784 AC-3 · 773 AC-3). 홈 · `oidc_subject` · `email` · `display_name` 쓰기 0. 중앙 신원 해소(born-unified)도 새 행일 때만 — 기존 행의 신원 링크는 그대로.
- 새 테넌트의 슬러그는 새것이므로 «이미 그 테넌트 소속» 은 생기지 않는다 — 슬러그 충돌은 지금처럼 `409 TENANT_ALREADY_EXISTS`.
- 소속 추가가 테넌트 생성 뒤 실패하면 지금처럼 테넌트를 SUSPEND 로 보상한다(ADR-044 D3) — 기존 행에는 아무것도 남지 않는다(역할 행 · 배정이 한 트랜잭션).
- 권한 캐시: 기존 행의 캐시를 무효화한다(소속 추가 — D-5).

**신원당 상한 (OD-1, 2026-10-11 UTC — 값 · 셈 · 경합은 773 OD-3, 2026-10-15 UTC)**:

- **세는 단위** = ADR-082 D4: 그 운영자 행의 `TENANT_ADMIN` 역할 행이 가리키는 **서로 다른 테넌트 수**(`tenant_id <> '*'`, 출처 무관 — 셀프 온보딩 · 초대 수락 · 역할 변경 · 그룹 fan-out). 판정 = `count ≥ 상한` → 거절(새 테넌트를 더하면 상한을 넘는다).
- 🔴 **테넌트 상태 무관 (773 OD-3)** — `ACTIVE` · `SUSPENDED` · `PENDING` 테넌트를 모두 센다(테넌트 상태를 읽지 않는다 — 784 구현 그대로). `ACTIVE` 만 세면 «만들기 → 정지되기 → 또 만들기» 순환이 열린다(784 S1 § 4-2 의 미결 — 여기서 닫는다). 대가: 정지된 회사도 자리를 차지한다.
- **값** = 설정 `admin.onboarding.max-owned-tenants-per-operator`, ~~기본 2~~ → **기본 3 (773 OD-3 · 구현 S3)**. 2 면 «초대받은 회사 1 + 내 회사 1» 인 사람이 자기 두 번째 회사를 못 만든다(단위가 출처 무관이라 초대 수락으로 받은 `TENANT_ADMIN` 도 센다). 설정 키 하나 — 코드 변경 없이 바꿀 수 있다.
- 🔴 **경합 (773 OD-3 — 784 S5 기록 `:635` «동시 두 요청이 cap+1» 을 닫는다 · 구현 S3)**: 상한 판정은 **두 번** 한다 — ① 테넌트 생성 **전**(지금 자리 — 빠른 거절, 아무것도 안 만든다) ② 소속 추가 트랜잭션 **안**에서 그 운영자 행을 **잠근 뒤**(`SELECT … FROM admin_operators WHERE id = ? FOR UPDATE`) 다시 센다. ② 가 넘으면 `422 OPERATOR_OWNED_TENANT_LIMIT_EXCEEDED` 로 롤백하고 방금 만든 테넌트를 SUSPEND 로 보상한다(ADR-044 D3 — 다른 실패와 같은 길). 같은 행에 대한 동시 두 요청은 잠금에서 줄을 서므로 상한을 넘는 쪽은 정확히 거절된다(S3 시험: 상한 −1 인 행으로 동시 두 요청 → 하나 `201` · 하나 `422` · 그 행의 `TENANT_ADMIN` 테넌트 수 = 상한).
- 새 행을 만드는 호출자(측면 없음 — 표 1행)는 셀 것이 0 이라 걸리지 않는다(동시 두 요청은 위 Errors 의 `oidc_subject` UNIQUE 경합 행). 초대 수락 — **첫 관리자 초대 수락 포함**(admin-api § Tenant Lifecycle) — 은 이 상한을 보지 않는다 — 상한은 «스스로 만드는» 테넌트의 남용 방지(ADR-044 D4)다.
- 🔵 이 상한이 없으면 772 의 409 를 걷는 순간 측면 있는 계정이 회사를 **무제한** 만든다(784 AC-0 F14 — 772 의 409 가 상한 1 의 역할을 겸하고 있었다).
- 🔵 **ADR-044 D4 «per-identity» 의 충족 근거 (773 OD-8)** — 신원당 rate 카운터를 따로 두지 않는다. 이 상한이 한 신원의 생성 **총량**을 3 으로 묶으므로 «한 신원의 반복 생성» 은 상한이 막고, «여러 신원을 찍어 내는» 반복은 인증 이메일 + 게이트웨이 per-IP 가 막는다.

**트러스트 게이트 — 인증된 이메일 (TASK-MONO-773 · ADR-MONO-044 D4 · 구현 S3)**:

- **자리**: 토큰 검증 뒤, 테넌트 생성 **전**(위 판정 표 0). `SUPER_ADMIN` 경로(admin-api § POST /api/admin/tenants)만 이 게이트 밖이다(그 경로의 «인증» 은 첫 관리자의 초대 수락이 한다).
- 🔴 **두 자격 (소유자 결정 2026-10-15 UTC — 773 S3, option A · S1 열린 질문 2 의 «수용 + 안내» 를 대체)** — 먼저 호출자의 운영자 측면(`oidc_subject = sub`)과 권위 계정(account-service `GET /internal/accounts/{sub}`)을 읽는다. 🔴 **계정 읽기의 404 는 오류가 아니라 판정 입력이다** — 컨트롤러는 측면 조회 전에 계정 행을 요구하지 않는다(773 이전 코드는 이 404 를 `503` 으로 끝냈다 — 아래 «773 이전 동작»):

  | 계정 읽기 | 운영자 측면 | 자격 | 이메일 판정 | 이어서 |
  |---|---|---|---|---|
  | `200`(계정 행 있음) | 무관 | **개인(풀) 계정 후보** | 772 매치(아래 매핑) — `MATCHED` 만 통과 | 측면 표 1 ~ 5행 |
  | `404 ACCOUNT_NOT_FOUND` | 있다 | **`iam` 자격 운영자**(관리자가 만든 자격 — 셀프 가입이 아니다) | **면제** — 매치를 부르지 않는다 | 측면 표 **2 ~ 5행만**(`'*'` · 비-`ACTIVE` → `409` · 상한 → `422` · 상한 미만 → 소속 추가 + `ORIGIN` 행). 🔴 **상한 3 · 게이트웨이 rate limit 은 그대로 걸린다** |
  | `404 ACCOUNT_NOT_FOUND` | 없다 | 없음 | — | `403 ONBOARDING_ACCOUNT_NOT_ELIGIBLE`(fail-closed — 이 `sub` 를 보증하는 것이 아무것도 없다) |
  | 그 밖의 4xx · 5xx · 타임아웃 · circuit-open | — | — | — | `503 DOWNSTREAM_ERROR` / `CIRCUIT_OPEN`(fail-closed) |

  - **왜 면제인가**: ADR-044 D4 의 인증 이메일 게이트는 **익명 셀프 가입**을 겨눈다. `iam` 자격은 관리자가 만든 자격이고 셀프 가입 경로가 없다. 그 사람이 회사를 만드는 길은 «이미 가진 운영자 행에 소속 추가» 하나이므로 이메일이 필요 없다(소속 추가는 이메일 · 이름을 읽지도 쓰지도 않는다 — 784 S5). 772 매치는 **넓히지 않는다**.
  - 🔴 **판별자는 대리(proxy)다** — «계정 행 없음 ∧ 측면 있음» 은 «`iam` 자격» 의 정의가 아니라 그 관측이다(시드의 `iam` 자격 넷 모두 account-service 계정 행이 없다 — 773 S3 기록). 알려진 두 어긋남:
    1. **넓은 쪽(위험)**: 계정 행이 지워졌는데 운영자 측면이 남은 개인 계정도 면제된다. 그래도 **상한 3 이 그 행을 묶는다**(새 측면을 만들 수 없다 — 측면 없는 404 는 거절) · rate limit 도 그대로.
    2. **좁은 쪽(안전)**: account-service 계정 행이 **있는** `iam` 자격은 1행(개인 계정 후보)으로 가서 매치가 404 → `403 ONBOARDING_ACCOUNT_NOT_ELIGIBLE`. 거절 쪽으로 틀린다.
    판별을 정의로 바꾸려면(auth-service 의 자격 종류 신호 — 토큰 클레임 또는 내부 조회) 별도 결정이다.
- **개인(풀) 계정의 판정 = 772 의 것을 그대로 부른다** — admin → account [`POST /internal/accounts/{accountId}/verified-email:match`](internal/admin-to-account.md#post-internalaccountsaccountidverified-emailmatch--인증된-이메일-일치-판정-task-mono-772)(admin-service `VerifiedEmailMatchPort` — 772 수락이 쓰는 그 포트). 입력: `accountId` = 토큰 `sub` · `expectedEmail` = 이 컨트롤러가 이미 권위 계정에서 읽은 이메일. 사본을 만들지 않는다(`VerifiedEmailRequirement` 는 account-service 한 자리).
- **매핑** (계정 읽기 `200` 일 때):

  | account-service 답 | 이 엔드포인트 |
  |---|---|
  | `200` + `emailVerifiedAt` | 통과 → 측면 판정 |
  | `404 ACCOUNT_NOT_FOUND`(풀 계정이 아니다 · 비-`ACTIVE`) | `403 ONBOARDING_ACCOUNT_NOT_ELIGIBLE` |
  | `403 ACCOUNT_EMAIL_MISMATCH`(권위 이메일을 읽은 뒤 바뀐 경합) | `403 ONBOARDING_ACCOUNT_NOT_ELIGIBLE` — 다시 부르면 새 이메일로 판정된다 |
  | `403 EMAIL_NOT_VERIFIED` | `403 EMAIL_NOT_VERIFIED`(770 공용 이름 그대로) |
  | 그 밖의 4xx · 5xx · 타임아웃 · circuit-open · `emailVerifiedAt` 없는 `200` | `503 DOWNSTREAM_ERROR` / `CIRCUIT_OPEN` — **fail-closed**, 아무것도 안 만든다 |

- 🔴 **누가 막히나 (의도)**: 계정 행이 있지만 `ACTIVE` 개인(풀) 계정이 아닌 호출자 — 사이트 계정 · B2B 계정 · 비-`ACTIVE` 풀 계정 · (위 «좁은 쪽») 계정 행이 있는 `iam` 자격 — 그리고 계정 행도 측면도 없는 `sub`. `'*'` 측면은 어차피 2행에서 409 다. 거절은 **안내 문구를 싣는다** — 콘솔 «테넌트 생성» 화면 문구([console-integration-contract.md § 2.6.4](../../../../platform-console/specs/contracts/console-integration-contract.md) 오류 표 — 남은 모집단의 중심인 사이트 · B2B 계정에 맞춰 «개인 계정으로 로그인해 만드세요» 를 유지한다). 응답 본문 `message` 도 같은 뜻의 문장으로 둔다(코드는 그대로 — 콘솔은 코드로 문구를 고른다).
- ~~🔴 소유자 결정 (2026-10-15 UTC — 773 S1 열린 질문 2): 받아들인다 — `iam` 자격 회사 운영자는 S3 뒤 `403 ONBOARDING_ACCOUNT_NOT_ELIGIBLE`~~ → **대체됨 (소유자 결정 2026-10-15 UTC, 773 S3)**: S3 의무로 잰 영향 모집단이 시드 **4**(`iam` 자격 `ACTIVE` 비-`'*'` 운영자 — demo@demo.com 포함)여서 다시 물었고, 답은 위 «두 자격» 의 면제다(option A).
- 🔵 **773 이전 동작 (S3 측정 — 시드 기준, 라이브 ⚪)**: `iam` 자격 운영자는 773 이전에도 이 엔드포인트로 회사를 만들 수 **없었다** — 컨트롤러가 측면을 보기 전에 `GET /internal/accounts/{sub}` 를 부르고, `iam` 자격에는 account-service 계정 행이 없어 404 → `503 DOWNSTREAM_ERROR` 로 끝났다. 그래서 면제는 «지키는» 변경이 아니라 «여는» 변경이다.
- 이 판정은 **붙일 때의 증거**다(ADR-080 D5) — 이미 만든 회사 · 측면을 이메일 상태로 회수하지 않는다.
- S3 시험(대조군 — 실패 먼저): 미인증 풀 계정 → `403 EMAIL_NOT_VERIFIED` · 테넌트 행 0 · 운영자 행 0 · 세트 행 0 → 같은 계정 인증 뒤 같은 요청 → `201`. bite: 게이트를 빼면 첫 칸만 빨강. `iam` 자격 운영자(계정 404 ∧ `ACTIVE` 회사 측면) → `201` · 매치 호출 0 · 새 테넌트에만 행 + `ORIGIN` · 기존 회사 행 바이트 불변. 사이트 계정(계정 200 · 매치 404 · 측면 없음) → `403 ONBOARDING_ACCOUNT_NOT_ELIGIBLE`. 계정 404 ∧ 측면 없음 → `403 ONBOARDING_ACCOUNT_NOT_ELIGIBLE`.

**회사 생성자의 업무 권한 세트 (TASK-MONO-773 OD-4 · ADR-MONO-083 OD-5 · ADR-MONO-044 D2 · 구현 S3)**:

- 생성 원자 묶음이 생성자에게 **세트 행 하나**를 쓴다: `operator_domain_set_grant (operator_id = 생성자 행, tenant_id = 새 테넌트, set = 프리셋 all-domains-full, source_key = 'ORIGIN', granted_by = NULL)`. 역할 행 · 배정과 **같은 admin 트랜잭션**(롤백이면 함께 없다 · 테넌트는 SUSPEND 보상).
- 🔴 **새 테넌트에만** (ADR-044 D2) — 생성자의 다른 회사 세트 행은 읽지도 쓰지도 않는다(**바이트 불변** — 773 AC-3 대조군). 다른 테넌트 · `'*'` 를 가리키는 `ORIGIN` 행은 구조적으로 생기지 않는다(`tenant_id` = 이 요청이 방금 만든 테넌트 하나).
- 🔴 **구독 검사를 하지 않는다 — ADR-083 D5 의 한 자리 예외.** 새 테넌트는 구독 0 으로 태어나므로(ADR-044 D6) 배정 API 의 규칙(«세트의 도메인 ⊆ entitled» — `422 DOMAIN_NOT_SUBSCRIBED`)대로면 어떤 세트도 줄 수 없고, `all-domains-full` 은 `fan`(고객 테넌트가 구독할 수 없는 도메인)을 담아 **영원히** 줄 수 없다(773 AC-0 § 8-2). 그래서 이 행은 배정 API 가 아니라 **생성 원자 묶음 한 자리**에서만 쓰고, 상한은 쓰기 게이트가 아니라 **토큰의 `∩ entitled`**(ADR-083 D6 · P3 — auth-service)다: 구독 전 assume `roles` = 없음 → `wms` 구독 → `wms` 카탈로그 역할 전부 · `fan` 역할은 영원히 0. 권한이 entitled 밖으로 넓어지지 않는다.
- 출처 `ORIGIN` 의 성질(정본: [admin-api.md § 출처와 회수 규칙](admin-api.md#출처-source_key-와-회수-규칙-adr-083-d4) · [data-model.md § operator_domain_set_grant](../../services/admin-service/data-model.md#operator_domain_set_grant)): API 로 만들 수 없다(`MIGRATION` 과 같은 «시스템 전용» 출처) · 개인 회수(`DELETE …/operators/{id}/domain-permission-sets/{setId}`)가 `DIRECT` · `MIGRATION` 과 함께 지운다 · «이 회사에서 내보내기» 가 지운다 · 감사 행 없음(관리 행위자가 없다 — 이 경로의 다른 쓰기와 같다).
- S3 시험(773 AC-2 · AC-3): 비운영자 생성 → 새 테넌트 assume `roles` = 없음(대조) → `wms` 구독 → `roles` = `wms` 카탈로그 8개 · `fan` 역할 0 · 생성자의 다른 테넌트 세트 행 0. 두 번째 회사 생성 → 새 테넌트에만 `ORIGIN` 행 · 기존 회사 세트 행 · 역할 행 바이트 불변 · 새 회사에서 기존 회사 권한 불통.

---

## POST /api/admin/onboarding/received-invitations — 받은 초대 (TASK-MONO-773 OD-7 · 구현 S3)

로그인한 사람이 **자기 인증된 이메일로 온** 운영자 초대(772)를 본다. 콘솔 비운영자 셸의 «테넌트 생성» 화면이 빈 상태에서 «받은 초대» 를 먼저 보여 주기 위한 읽기다(773 Edge Case 2). 🔴 **읽기 전용 (소유자 결정 2026-10-15 UTC — 773 S1 열린 질문 6)**: 이 표면 · 콘솔 화면 어디에도 수락 동작이 없다 — «링크 없이 수락»(subject token + 인증 이메일)은 **열지 않는다**. 수락은 772 경로 그대로 — 초대 메일의 링크(IdP 화면, [auth-api.md § 운영자 초대 수락](auth-api.md#idp-브라우저-화면--운영자-초대-수락-task-mono-772--adr-mono-080-d6))가 한다. 관리 목록 `GET /api/admin/operator-invitations` 는 `operator.manage` 게이트라 운영자 토큰 없는 사람이 쓸 수 없다(773 AC-0 § 8-7) — 이것은 그 목록의 «받는 사람» 판이다.

**Auth required**: 위 `POST …/organizations` 와 **같은** 자기 흐름 — 본문의 `subjectToken`(콘솔 client OIDC access token, `token_type` 클레임 없음), `IamOidcSubjectTokenValidator`. 운영자 토큰 없음 · `X-Operator-Reason` 없음. `permitAll` + `OperatorAuthenticationFilter` 건너뜀 — 서브트리 예외표: [admin-api.md § Exceptions](admin-api.md#exceptions-no-operator-jwt-required).
🔵 **읽기인데 `POST` 인 이유**: subject token 을 본문에 싣는다(이 표면에는 사용자 JWT 헤더 인증이 없다 — 위 엔드포인트와 같다). 토큰을 쿼리 문자열에 싣지 않는다(접근 로그). **아무것도 쓰지 않는다** — 감사 행 · 이벤트 없음. 게이트웨이 `onboarding` scope 를 걸지 않는다(위 Rate limit).

**Request**:
```json
{ "subjectToken": "string (required — the caller's IAM OIDC access token)" }
```

**Response 200** — 판정 결과가 무엇이든 `200` 이다(«없음» 도 답이다):
```json
{
  "accountState": "VERIFIED",
  "items": [
    {
      "invitationId": "0192…",
      "tenantId": "acme-corp",
      "tenantDisplayName": "Acme Corp",
      "roles": ["SUPPORT_LOCK"],
      "firstAdmin": false,
      "expiresAt": "2026-10-22T10:00:00Z",
      "invitedAt": "2026-10-15T10:00:00Z"
    }
  ]
}
```

| 필드 | 뜻 |
|---|---|
| `accountState` | 인증 이메일 판정(위 «트러스트 게이트» 와 **같은 호출 · 같은 매핑**): `VERIFIED` · `EMAIL_NOT_VERIFIED`(→ `items` = `[]`, 화면은 이메일 인증 안내) · `NOT_ELIGIBLE`(풀 계정 아님 · 비-`ACTIVE` · 이메일 경합 → `items` = `[]`; 🔵 계정 읽기가 404 인 호출자 — `iam` 자격 운영자 포함 — 도 `NOT_ELIGIBLE` + `[]`: 인증된 이메일이 없으니 열쇠가 없다. 온보딩의 이메일 면제는 이 읽기에 옮겨지지 않는다). 판정을 못 받으면 이 필드를 지어내지 않고 `503`(아래) |
| `items` | `accountState = VERIFIED` 일 때만 채운다: `operator_invitation` 중 `email` = 그 계정의 인증된 이메일(trim · 소문자) ∧ `status = PENDING` ∧ `expires_at > 읽은 시각`. `createdAt` 내림차순 · 최대 50(그 이상은 자른다 — 한 주소로 대기 초대가 50을 넘는 경우는 정상이 아니다) |
| `tenantDisplayName` | account-service 테넌트 읽기 — 실패하면 `null`(화면은 `tenantId`) · fail-soft(772 미리보기와 같은 규칙) |
| `firstAdmin` | 그 초대가 «첫 관리자 초대» 인가(admin-api § Tenant Lifecycle) — 화면 문구가 «{회사}의 첫 관리자로 초대되었습니다» 로 바뀐다 |
| `invitedAt` | 살아 있는 토큰의 발급 시각(재발송이면 재발송 시각) |

- 🔴 **숨기는 것**: 토큰 · 토큰 해시 · 링크(R4 — 이 표면은 링크를 다시 주지 않는다: 메일함을 가진 사람만 수락한다는 772 의 증거를 바꾸지 않는다) · 초대자 운영자 id · `delivery` · `ACCEPTED` · `CANCELLED` · **만료된** 초대 · 다른 이메일로 온 초대(입력은 `sub` 하나 — 이메일을 요청에서 받지 않는다, 남의 주소로 열거할 수 없다) · 미인증 주소로 온 초대(인증하기 전에는 그 주소의 주인이라는 증거가 없다).
- 일반 초대가 `PENDING` 테넌트를 향하는 경우(수락하면 `409 OPERATOR_INVITATION_INVALIDATED` 일 초대)도 그대로 보인다 — 판정은 수락이 한다. 🔵 `SUPER_ADMIN` 경로의 첫 관리자 초대는 테넌트가 `PENDING` 일 때 수락 가능한 초대다.
- 이 읽기는 운영자 토큰을 가진 사람도 부를 수 있다(같은 subject token) — 콘솔은 비운영자 셸에서만 부른다.

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | `subjectToken` 누락 |
| 401 | `TOKEN_INVALID` (or `UNAUTHORIZED`) | `subjectToken` 검증 실패 — 위 엔드포인트와 같다 |
| 503 | `DOWNSTREAM_ERROR` / `CIRCUIT_OPEN` | 인증 이메일 판정을 못 받았다(fail-closed — 빈 목록으로 답하지 않는다: «초대 없음» 과 «모른다» 를 섞지 않는다) |
