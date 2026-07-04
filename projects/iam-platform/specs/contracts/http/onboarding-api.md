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
| 5xx | `DOWNSTREAM_ERROR` / `CIRCUIT_OPEN` | account-service 도달 실패 — 테넌트 생성 후면 SUSPEND 보상 |

**Side Effects**:
- account-service `tenants` row 생성 (+ account-side `tenant.created` 이벤트).
- admin-service `admin_operators` + `admin_operator_roles`(TENANT_ADMIN·TENANT_BILLING_ADMIN, tenant-scoped) + `operator_tenant_assignment` 생성.
- born-unified 중앙 identity resolve/create (fail-soft).

**Out of scope (ADR-044 deferred)**: 이메일 인증 강제(D4, 슬라이스는 인증만), 승인 큐(D4-C), 도메인 auto-subscribe(D6-B), org 프로필 관리, billing, UI.
