# Task ID

TASK-MONO-262

# Title

ADR-MONO-035 **4b-2a** (ADR-032 D5 step 4 / D3) — drop the now-dead `account_type` admission legs and `X-Account-Type` injection at the gateways. ecommerce `AccountTypeEnforcementFilter` + wms `AccountTypeValidationFilter` become **roles-only** (the `account_type` OR-branch is inert post-4a — operators now carry domain roles, consumers carry `CUSTOMER`); all four gateways' `JwtHeaderEnrichmentFilter` stop injecting `X-Account-Type` (no downstream reader — verified across all projects). Safe now: `account_type` is still emitted (4b-2b stops it), but nothing depends on it at the gateways once this lands.

# Status

ready

# Owner

backend

# Task Tags

- gateway
- security
- adr-035

---

# Dependency Markers

- **executes**: ADR-MONO-035 O5 step 4b (gateway leg drop + `X-Account-Type` removal) / ADR-032 D3 (role-based admission replaces the `account_type` partition).
- **depends on**: 4a (BE-376, merged — operators carry domain roles) + 4b-1 (MONO-261, merged — web-store role-based). The roles leg now admits every legit token, so the `account_type` leg is dead weight.
- **independent of / before-or-after**: 4b-2b (stop emitting `account_type`). The `account_type` leg is inert whether the claim is present (roles already admit) or absent (`"OPERATOR".equals(null)`=false → roles decide), so this can land before or after 4b-2b. Sequenced before 4b-2b here for a clean (no inert-leg) end state.
- **no downstream X-Account-Type reader**: verified — zero non-gateway service reads the header (ecommerce/wms/scm/fan/erp/finance/platform-console).

# Goal

Each gateway admits purely on `roles` (the `account_type` OR-branch removed); no gateway injects `X-Account-Type`. The `IdentityHeaderStripFilter` X-Account-Type entry stays (inert defense-in-depth — clients still can't spoof it).

# Scope

- `projects/ecommerce-microservices-platform/apps/gateway-service/.../filter/AccountTypeEnforcementFilter.java` — `/api/admin/**`: `hasRole(roles,"ADMIN")` (drop `|| "OPERATOR".equals(accountType)`); else: `hasRole(roles,"CUSTOMER")` (drop `|| "CONSUMER".equals(accountType)`). Remove the `accountType` read. Keep the 403 messages + order + public-passthrough.
- `projects/wms-platform/apps/gateway-service/.../filter/AccountTypeValidationFilter.java` — `isOperator` = `roles != null && !roles.isEmpty()` (drop `|| "OPERATOR".equals(account_type)`). Remove the `account_type` read.
- `JwtHeaderEnrichmentFilter` in **all four** gateways (ecommerce, wms, scm, fan) — remove the `String accountType = jwt.getClaimAsString("account_type"); if (accountType != null) builder.header("X-Account-Type", accountType);` block. Keep `X-User-Role`/`X-User-Id`/`X-User-Email`/`X-Tenant-Id`/`X-Actor-Id` exactly.
- Tests — update each filter's test: `AccountTypeEnforcementFilterTest` (remove the `account_type`-only-admit cases; keep/strengthen the roles cases + the no-role 403 case), `AccountTypeValidationFilterTest` (same), each `JwtHeaderEnrichmentFilterTest` (remove `X-Account-Type` assertions; assert it is NOT set), `JwtTestHelper`/`GatewayIntegrationTest` may keep `account_type` in built JWTs (harmless) — but the enrichment tests must assert no `X-Account-Type` header.
- NO contract change (4b-2b finalizes `jwt-standard-claims.md`). NO class renames (the filters keep their names; a rename to `RoleAdmissionFilter` is an optional later cleanup). NO `IdentityHeaderStripFilter` change.

# Acceptance Criteria

- **AC-1** ecommerce gateway admits `/api/admin/**` iff `roles` contains `ADMIN`, other authed paths iff `roles` contains `CUSTOMER`; no `account_type` read remains.
- **AC-2** wms gateway admits iff `roles` is non-empty; no `account_type` read remains.
- **AC-3** No gateway injects `X-Account-Type` (the enrichment tests assert its absence).
- **AC-4** Public-route passthrough + 403-on-miss semantics + filter order unchanged.
- **AC-5** Each affected project's Docker-free `:test` green; CI `Build & Test` + the gateway test jobs are authoritative.

# Related Specs

- `docs/adr/ADR-MONO-035-operator-auth-unification-model.md` (§ O5 4b) + `docs/adr/ADR-MONO-032-unified-identity-roles-model.md` (§ D3 gateway role-based admission, `X-Account-Type` dropped)

# Related Contracts

- `platform/contracts/jwt-standard-claims.md` § Gateway Enforcement / `X-Account-Type` — finalized in 4b-2b; this task implements the role-based admission the contract already targets.

# Edge Cases

- A token with no `roles` (e.g. account-service outage during issuance with no seed) → 403 at the gateway (least-privilege; same as the prior empty-roles behavior).
- `account_type` still present on tokens (4b-2b not yet landed) → ignored by the gateways now (roles decide).

# Failure Scenarios

- If a gateway still reads `account_type` after this → the leg lingers; the ADR's role-based admission (D3) is not fully realized.
- If `X-User-Role` enrichment is accidentally changed → downstream services that derive consumer-vs-operator from `X-User-Role` break (must stay exactly).
