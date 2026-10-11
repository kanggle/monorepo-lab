# HTTP Contract: admin-service — Self-Service Tenant Onboarding

TASK-BE-474 / [ADR-MONO-044](../../../../../docs/adr/ADR-MONO-044-self-service-tenant-onboarding.md) (ACCEPTED).

Self-service B2B tenant onboarding: an authenticated visitor creates a NEW tenant and is
appointed its first `TENANT_ADMIN` + `TENANT_BILLING_ADMIN`, with **no platform `SUPER_ADMIN`
in the loop** (AWS "create account → root" / GCP "create project → owner" parity). Everything
downstream (managing the tenant's operators/subscriptions) is [ADR-MONO-024](../../../../../docs/adr/ADR-MONO-024-tenant-admin-delegation.md).

Base path: `/api/admin/onboarding`. All endpoints route via the IAM gateway.

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
| 409 | `TENANT_ALREADY_EXISTS` | `tenantId` 슬러그 중복 (보상 불필요 — 아무것도 생성 안 됨) |
| 409 | `OPERATOR_EMAIL_CONFLICT` | (희귀) 새 테넌트에 동일 이메일 operator 존재 |
| 409 | `OPERATOR_ALREADY_PROVISIONED` | **TASK-MONO-772** 로 생기고 **TASK-MONO-784 (ADR-MONO-082 b · D-12 — 구현 S5)** 로 좁혀진다 — 호출자 계정(`sub`)의 기존 운영자 측면(`admin_operators.oidc_subject = sub`)이 **새 회사의 소속을 받을 수 없다**: `'*'` 측면(ADR-082 D2 · ADR-080 D1 — 플랫폼 관리자는 회사 소속을 받지 않는다) · 비-`ACTIVE` 측면. **테넌트를 만들기 전에** 판정한다 — 아무것도 생성 안 됨, 보상 불필요. `ACTIVE` 회사 측면은 이 코드가 아니라 아래 «소속 추가» 다. (784 S5 머지 전 동작: 측면이 있으면 상태 무관 이 409 — 772 OD-1) |
| 422 | `OPERATOR_OWNED_TENANT_LIMIT_EXCEEDED` | **TASK-MONO-784 (소유자 결정 OD-1 · ADR-MONO-044 D4 · ADR-MONO-082 D4 — 구현 S5)** — 호출자의 기존 운영자 측면이 이미 신원당 상한만큼의 테넌트에서 테넌트 관리자다. **테넌트를 만들기 전에** 판정한다 — 아무것도 생성 안 됨. 아래 «신원당 상한» |
| 5xx | `DOWNSTREAM_ERROR` / `CIRCUIT_OPEN` | account-service 도달 실패 — 테넌트 생성 후면 SUSPEND 보상 |

**Side Effects**:
- account-service `tenants` row 생성 (+ account-side `tenant.created` 이벤트).
- admin-service `admin_operators` + `admin_operator_roles`(TENANT_ADMIN·TENANT_BILLING_ADMIN, tenant-scoped) + `operator_tenant_assignment` 생성.
  **784 S5 부터**: 호출자에게 이미 `ACTIVE` 회사 운영자 측면이 있으면 `admin_operators` 는 만들지 않고 **그 행에** 새 테넌트의 `admin_operator_roles`(TENANT_ADMIN · TENANT_BILLING_ADMIN, `tenant_id` = 새 테넌트) + `operator_tenant_assignment` 만 더한다 — 아래 «다회사 운영자와 온보딩».
- born-unified 중앙 identity resolve/create (fail-soft).

**Out of scope (ADR-044 deferred)**: 이메일 인증 강제(D4, 슬라이스는 인증만), 승인 큐(D4-C), 도메인 auto-subscribe(D6-B), org 프로필 관리, billing, UI.

**772 와 온보딩 (TASK-MONO-772 · ADR-MONO-080 D6 · D9)**:

- **누가 이 토큰을 들고 올 수 있나** — 772 부터 **운영자 측면이 있는** 풀 계정도 `platform-console-web` 토큰을 받는다([auth-api.md § 풀 계정의 콘솔 토큰](auth-api.md#풀-계정의-콘솔-토큰--운영자-측면이-있을-때만-task-mono-772--adr-mono-080-d6)). 측면 **없는** 풀 계정은 여전히 못 받는다 — 그래서 풀 계정의 셀프 온보딩은 772 에서도 **닫혀 있다**(이 엔드포인트에 인증 이메일 게이트가 없으므로 772 는 그 구멍을 열지 않는다, 772 AC-0 F9). 여는 것은 `TASK-MONO-773`(ADR-080 D9 = T1 — 비운영자 셸 · ADR-044 D4 트러스트 게이트)이다.
- 🔴 **이미 운영자 측면이 있는 호출자 (OD-1)** — 지금 코드는 테넌트를 만든 뒤 `oidc_subject` 를 쓰다 플랫폼 전역 UNIQUE 에 걸리고(`FirstAdminProvisioner` — «first-time onboarder 에게만 비어 있다»), 테넌트를 SUSPEND 로 보상한다(정지된 빈 테넌트가 남는다). 772 가 측면 있는 풀 계정에 콘솔 토큰을 주면서 이 모집단이 넓어지므로, 772 는 이것을 **테넌트 생성 전의 `409 OPERATOR_ALREADY_PROVISIONED`** 로 바꾼다(위 Errors — 초대 수락의 OD-1 판정과 같은 술어 · 같은 코드). 이미 운영자인 사람의 «두 번째 회사» 는 **`ADR-MONO-082` = b(ACCEPTED 2026-10-10 UTC) · `TASK-MONO-784`** 가 정했다 — 아래 «다회사 운영자와 온보딩». 구현 슬라이스: 772 S3(수락과 같은 판정 자리) → 784 S5(«소속 추가» 로).
- `IamOidcSubjectTokenValidator` 와 이 엔드포인트의 토큰 검증(`OnboardingController` — `amr` 을 보지 않는다)은 772 에서 바꾸지 않는다(772 AC-0 «773 을 막지 않는 조건» ⑤).

**다회사 운영자와 온보딩 (TASK-MONO-784 · ADR-MONO-082 b D2 · D4 · D6 b행 · 구현자 결정 D-12 · 소유자 결정 OD-1 — 구현 S5)**:

판정 순서 — 토큰 · 본문 검증 뒤, **테넌트를 만들기 전에** 처음 실패한 것이 답한다:

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

**신원당 상한 (OD-1, 2026-10-11 UTC)**:

- **세는 단위** = ADR-082 D4: 그 운영자 행의 `TENANT_ADMIN` 역할 행이 가리키는 **서로 다른 테넌트 수**(`tenant_id <> '*'`, 출처 무관 — 셀프 온보딩 · 초대 수락 · 역할 변경 · 그룹 fan-out). 판정 = `count ≥ 상한` → 거절(새 테넌트를 더하면 상한을 넘는다).
- **값** = 설정 `admin.onboarding.max-owned-tenants-per-operator`, **기본 2**. 773 AC-0 이 값을 다시 정한다(ADR-082 D4 — 값은 773 의 몫, 784 는 세는 장치만 싣는다). 기본 2 = 784 AC-3 이 필요한 모양(두 번째 회사)이 정확히 열린다.
- 새 행을 만드는 호출자(측면 없음 — 표 1행)는 셀 것이 0 이라 걸리지 않는다. 초대 수락은 이 상한을 보지 않는다 — 상한은 «스스로 만드는» 테넌트의 남용 방지(ADR-044 D4)다.
- 🔵 이 상한이 없으면 772 의 409 를 걷는 순간 측면 있는 계정이 회사를 **무제한** 만든다(784 AC-0 F14 — 772 의 409 가 상한 1 의 역할을 겸하고 있었다).
