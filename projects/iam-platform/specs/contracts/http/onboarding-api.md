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
| 409 | `OPERATOR_ALREADY_PROVISIONED` | **TASK-MONO-772 (소유자 결정 OD-1 — 한 사람 = 한 회사)** — 호출자 계정(`sub`)에 이미 운영자 측면이 있다(`admin_operators.oidc_subject = sub`, 상태 무관). **테넌트를 만들기 전에** 판정한다 — 아무것도 생성 안 됨, 보상 불필요. 아래 «772 와 온보딩» 참조 |
| 5xx | `DOWNSTREAM_ERROR` / `CIRCUIT_OPEN` | account-service 도달 실패 — 테넌트 생성 후면 SUSPEND 보상 |

**Side Effects**:
- account-service `tenants` row 생성 (+ account-side `tenant.created` 이벤트).
- admin-service `admin_operators` + `admin_operator_roles`(TENANT_ADMIN·TENANT_BILLING_ADMIN, tenant-scoped) + `operator_tenant_assignment` 생성.
- born-unified 중앙 identity resolve/create (fail-soft).

**Out of scope (ADR-044 deferred)**: 이메일 인증 강제(D4, 슬라이스는 인증만), 승인 큐(D4-C), 도메인 auto-subscribe(D6-B), org 프로필 관리, billing, UI.

**772 와 온보딩 (TASK-MONO-772 · ADR-MONO-080 D6 · D9)**:

- **누가 이 토큰을 들고 올 수 있나** — 772 부터 **운영자 측면이 있는** 풀 계정도 `platform-console-web` 토큰을 받는다([auth-api.md § 풀 계정의 콘솔 토큰](auth-api.md#풀-계정의-콘솔-토큰--운영자-측면이-있을-때만-task-mono-772--adr-mono-080-d6)). 측면 **없는** 풀 계정은 여전히 못 받는다 — 그래서 풀 계정의 셀프 온보딩은 772 에서도 **닫혀 있다**(이 엔드포인트에 인증 이메일 게이트가 없으므로 772 는 그 구멍을 열지 않는다, 772 AC-0 F9). 여는 것은 `TASK-MONO-773`(ADR-080 D9 = T1 — 비운영자 셸 · ADR-044 D4 트러스트 게이트)이다.
- 🔴 **이미 운영자 측면이 있는 호출자 (OD-1)** — 지금 코드는 테넌트를 만든 뒤 `oidc_subject` 를 쓰다 플랫폼 전역 UNIQUE 에 걸리고(`FirstAdminProvisioner` — «first-time onboarder 에게만 비어 있다»), 테넌트를 SUSPEND 로 보상한다(정지된 빈 테넌트가 남는다). 772 가 측면 있는 풀 계정에 콘솔 토큰을 주면서 이 모집단이 넓어지므로, 772 는 이것을 **테넌트 생성 전의 `409 OPERATOR_ALREADY_PROVISIONED`** 로 바꾼다(위 Errors — 초대 수락의 OD-1 판정과 같은 술어 · 같은 코드). 이미 운영자인 사람의 «두 번째 회사» 는 `TASK-MONO-773` 착수 전 ADR(다회사 운영자 모델)이 정한다. 구현 슬라이스: 772 S3(수락과 같은 판정 자리).
- `IamOidcSubjectTokenValidator` 와 이 엔드포인트의 토큰 검증(`OnboardingController` — `amr` 을 보지 않는다)은 772 에서 바꾸지 않는다(772 AC-0 «773 을 막지 않는 조건» ⑤).
