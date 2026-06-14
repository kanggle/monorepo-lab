# Task ID

TASK-BE-370

# Title

assume-tenant **roles augmentation** — preserve the operator's `roles` from the validated subject token onto the assumed (`token_exchange`) token (ADR-MONO-033 S4 assume-tenant / ADR-MONO-032 D5 step 2, sub-step 3 — the final step-2 piece).

# Status

ready

# Owner

backend

# Task Tags

- backend
- iam
- security

---

# Dependency Markers

- **depends on**: TASK-BE-369 (MERGED — the operator's base GAP OIDC token now carries `roles`; the assume-tenant exchange validates that subject token, so the roles are available to preserve).
- **implements**: ADR-MONO-033 S4 (assume-tenant resolution locus). § 3.3 execution roadmap task 3 — **completes ADR-032 D5 step 2** (roles-only issuance across both base + assumed tokens).
- **mirrors**: TASK-BE-329 `operatorAccountType` preservation + TASK-BE-338 `org_scope` — the same "carry a value from the validated subject token / assignment onto the resolved grant, the customizer's assume-tenant branch injects it" pattern.

# Goal

When an operator assumes a customer tenant (RFC 8693 `token_exchange`), the assumed token should carry the operator's `roles` so downstream domain services (and any role-gating gateway, e.g. wms) see the operator's role set — exactly as the base token does after BE-369. The operator is one identity acting in a customer tenant; their roles travel with them.

# Design Resolution — preserve, don't re-resolve

The assume-tenant provider already **validates the operator's subject token** (their base GAP OIDC token) and extracts `oidcSubject` (sub) + `operatorAccountType` from it. After BE-369 that subject token **also carries `roles`**. So the assumed token's roles = the operator's subject-token roles, **preserved verbatim** — exactly parallel to how `operatorAccountType` is preserved (BE-329).

**Why preserve (not re-resolve via account-service):**
- The operator is **one identity**; their roles are the same whether on the base or the assumed token. Preserving = least-privilege, no union across tenants/identities (ADR-033 S4 "selected tenant only, no union" — trivially satisfied: it is exactly the one operator's own roles).
- The selected tenant is a **customer** tenant; the operator has no `account_roles` rows under it (their roles live under their home/login context). A re-resolve (`listAccountRoles(selectedTenant, operatorSub)`) would return empty → seed, and the assumed token's "platform" is not cleanly derivable (tenant_id = the customer tenant slug, not a domain name). Preservation sidesteps that and needs **no new lookup** (no fail-soft / availability concern — the subject token is already validated).
- The operator's actual authorization in the assumed context is the **fail-closed assignment gate** + `org_scope` + the carried `scope` (BE-336 `erp.write`) + domain RBAC/ABAC; the `roles` claim is the operator's identity-level role set, supplementary and now consistent with their base token.
- Finer per-selected-tenant role resolution is a future refinement (out of scope; the S3 `account_roles` aud column lineage).

# Scope

- **`AssumeTenantAuthenticationToken`**: add an `operatorRoles` (`List<String>`) field + getter + a new provider-side constructor overload carrying it (additive; existing constructors delegate with `null`/empty — mirror how `operatorAccountType`/`orgScope` were added). Null-safe.
- **`AssumeTenantAuthenticationProvider`**: after validating the subject token, extract `subjectJwt.getClaimAsStringList("roles")`; carry it onto the rebuilt `resolvedGrant` via the new constructor (alongside `operatorAccountType` + `orgScope`). No new dependency, no account-service call.
- **`TenantClaimTokenCustomizer.customizeForAssumeTenant`**: read `grant.getOperatorRoles()`; inject the `roles` claim when non-empty (parallel to `injectAccountType` + `org_scope`; omit when null/empty — net-zero). Do NOT alter the existing `tenant_id`/`tenant_type`/`account_type`/`org_scope`/`entitled_domains` injection.
- **Tests** (Docker-free Mockito):
  - `AssumeTenantAuthenticationProviderTest`: a subject JWT with `roles: ["ERP_OPERATOR"]` → the grant passed to `tokenGenerator.generate(...)` carries `operatorRoles == ["ERP_OPERATOR"]` (capture the `OAuth2TokenContext`, inspect `getAuthorizationGrant()`); subject JWT with no roles → `operatorRoles` empty/null (graceful).
  - `TenantClaimTokenCustomizerTest`: assume-tenant grant with `operatorRoles` → `roles` claim injected; empty/null `operatorRoles` → `roles` omitted; existing assume-tenant assertions (tenant_id/account_type/org_scope) still pass.

**Out of scope:** re-resolving roles per selected tenant; the `account_roles` aud column; any base-token change (BE-369 owns that).

# Acceptance Criteria

- **AC-1** The assumed (`token_exchange`) token carries the operator's `roles` (preserved verbatim from the validated subject token) when present.
- **AC-2** Empty/absent subject-token roles → assumed token omits `roles` (net-zero; gateway then 403s where it role-gates, correct).
- **AC-3** No new account-service call on the assume-tenant path (preserve, not re-resolve) — the fail-closed assignment gate semantics are unchanged.
- **AC-4** Existing assume-tenant injection (tenant_id / tenant_type / account_type / org_scope / entitled_domains) byte-unchanged.
- **AC-5** `apps/auth-service:test` GREEN (Docker-free Mockito). Testcontainers `@SpringBootTest` IT → CI Linux (the authoritative wiring check for the exchange path).

# Related Specs

- `docs/adr/ADR-MONO-033-roles-issuance-resolution-model.md` (S4 assume-tenant augmentation)
- `docs/adr/ADR-MONO-020-operator-multitenant-assignment.md` (the assume-tenant exchange this augments)
- `platform/contracts/jwt-standard-claims.md` (§ Standard Claims `roles`)

# Related Contracts

- `platform/contracts/jwt-standard-claims.md` § Standard Claims (`roles`) — now also on the assumed token.
- `projects/iam-platform/specs/contracts/http/internal/auth-to-admin.md` (the assignment gate — unchanged; this task adds no new edge).

# Edge Cases

- Subject token with `roles: ["WMS_OPERATOR","OUTBOUND_MANAGER"]` → assumed token carries both (verbatim).
- Subject token with no `roles` (legacy token issued before BE-369, or a seed-empty platform) → assumed token omits `roles` (graceful, net-zero).
- The assignment gate is unchanged and still fail-CLOSED — this task only adds the `roles` claim to a token that already passes the gate.
- `getClaimAsStringList("roles")` on a token whose `roles` is absent → null/empty; handle null-safe.

# Failure Scenarios

- If roles are re-resolved via an account-service call on the assume-tenant path → adds an availability dependency to a fail-CLOSED authorization gate (wrong policy mix) + a platform-determination problem. Preserve from the subject token instead (no new call).
- If the existing `account_type`/`org_scope`/`tenant_id` injection is altered → breaks BE-329/BE-338. Only ADD the `roles` injection.
- If `roles` is injected as a union across the operator's other tenants/assignments → violates least-privilege. Carry only the one operator's own subject-token roles, verbatim.
