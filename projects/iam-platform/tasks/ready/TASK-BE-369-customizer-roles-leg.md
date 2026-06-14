# Task ID

TASK-BE-369

# Title

`TenantClaimTokenCustomizer` **roles leg** — emit the signed `roles` claim at base-token issuance (ADR-MONO-033 S4 base + S3 / ADR-MONO-032 D5 step 2, sub-step 2). The behavioral step: `roles` starts being issued; the dual-read gateways' role leg goes live.

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

- **depends on**: TASK-BE-368 (MERGED — `AccountServicePort.listAccountRoles` + the account-service read EP this leg calls).
- **implements**: ADR-MONO-033 S4 (base-token resolution locus) + S3 (aud-scoping + aud-default seed). § 3.3 execution roadmap task 2.
- **child of**: ADR-MONO-032 D5 step 2 (roles-only issuance). This is the **behavioral** sub-step — net-positive, NOT net-zero: tokens now carry `roles`. Safe under the dual-read window: a token that previously passed via the `account_type` leg now ALSO satisfies the role leg; the `account_type` leg stays tolerant until ADR-032 step 4. **No token loses access.**
- **mirrors**: `populateEntitledDomains` (fail-soft + recursion guard).
- **out of scope** (separate task): assume-tenant (`token_exchange`) roles augmentation = ADR-033 S4 assume-tenant = TASK-BE-370 (③).

# Goal

Populate the signed `roles` claim on `authorization_code` / `refresh_token` access + id tokens from the authoritative `account_roles` store (via BE-368's port), aud-scoped and seeded per ADR-033 S3, fail-soft per S5 — so role-based gateway admission has real roles to admit on.

# Design Resolution — how "aud-scoping" is operationalized (S3/S4 mechanics)

ADR-033 S3/S4 framed scoping on the token's `aud`. **Investigation at base `b8780c54` found `aud` is NOT the platform**: Spring Authorization Server sets `aud` = **client_id** (e.g. `ecommerce-web-store-client`), and **no gateway validates `aud`** — gateways validate `tenant_id` (+ issuer) only (`TenantClaimValidator`). The platform signal is instead:

- **Platform** = the **registered client's `tenant_id`** (ClientSettings `SETTING_TENANT_ID`, seeded per client: `ecommerce` / `wms` / `fan-platform` / `scm` / `erp` — Flyway V0009/V0010/V0012/…). Available via `context.getRegisteredClient()`.
- **Surface** = `account_type` (CONSUMER / OPERATOR) from the principal details map (set by `CredentialAuthenticationProvider`). This disambiguates the ecommerce consumer (`CUSTOMER`) vs admin (`ADMIN`) surface, which share one platform.
- **Account** = `account_id` from the principal details map.
- **User tenant** = the `tenant_id` being injected (the claim) — used for the `account_roles` lookup key (roles are provisioned under the user's tenant; in the legacy single-tenant model `tenant_id` == platform, so the lookup is already platform-scoped).

So "aud-scoping" = **stored `account_roles` (already tenant-scoped) emitted verbatim if non-empty, else the aud-default seed keyed on (client-platform, account_type)**. The token's `aud` claim is left untouched (SAS default = client_id). This is a faithful operationalization of S3 (the only signals available); the explicit `account_roles` aud column remains S3-deferred.

**aud-default seed table** (applied only when the stored set is empty):

| client platform (`tenant_id`) | CONSUMER | OPERATOR |
|---|---|---|
| `ecommerce` | `["CUSTOMER"]` | `["ADMIN"]` |
| `fan-platform` | `["FAN"]` | `["FAN"]` |
| `wms` | — | `["WMS_OPERATOR"]` |
| `scm` | — | `["SCM_OPERATOR"]` |
| `erp` | — | `["ERP_OPERATOR"]` |
| `mes` | — | `["MES_OPERATOR"]` |
| (other / unknown) | `[]` | `[]` |

`PREMIUM_MEMBER` (needs a fan membership lookup) is out of scope — `FAN` only.

# Scope

- **`TenantClaimTokenCustomizer`** (`apps/auth-service`): add a `roles` leg invoked from `customizeForAuthorizationCode` (covers `authorization_code` AND `refresh_token` — the customizer already routes `REFRESH_TOKEN` → `customizeForAuthorizationCode`). Steps: extract `account_id` + `account_type` from principal details; resolve `platformTenantId` from the registered client's ClientSettings tenant_id; `stored = accountServicePort.listAccountRoles(claimTenantId, accountId)` (fail-soft, recursion-safe — NEVER on `client_credentials`); `roles = stored.isEmpty() ? seed(platformTenantId, accountType) : stored`; inject `roles` claim only when non-empty.
- **`RoleSeedPolicy`** (small package-private helper or static method): the seed table above + platform normalization. Pure, unit-testable.
- **fail-soft** (S5): `AccountServiceUnavailableException` / any failure → fall to the seed; if the seed is also empty → omit `roles` (do NOT throw — issuance must not depend on account-service; mirror `populateEntitledDomains`).
- **Tests** (`TenantClaimTokenCustomizerTest`, Docker-free Mockito): stored roles emitted verbatim; empty stored → seed by (platform, account_type) for ecommerce-consumer→CUSTOMER / ecommerce-operator→ADMIN / wms-operator→WMS_OPERATOR / fan→FAN; account-service failure → seed (fail-soft, no throw); `client_credentials` → `listAccountRoles` NEVER called (recursion guard, `verify(...never())`); missing account_id → no lookup, graceful.

**Out of scope:** assume-tenant augmentation (TASK-BE-370 / ③); the `account_roles` aud column (S3 deferred); `PREMIUM_MEMBER`.

# Acceptance Criteria

- **AC-1** `authorization_code` + `refresh_token` access & id tokens carry a `roles` claim sourced from `account_roles` (stored-if-present-else-seed), aud-scoped per the Design Resolution.
- **AC-2** Seed table correct: ecommerce CONSUMER→CUSTOMER, ecommerce OPERATOR→ADMIN, wms OPERATOR→WMS_OPERATOR, scm/erp/mes OPERATOR→`{PLAT}_OPERATOR`, fan→FAN; unknown platform→`[]`.
- **AC-3** Fail-soft: account-service failure / empty → seed or omit; issuance never throws on the roles lookup.
- **AC-4** Recursion-safe: `listAccountRoles` is NEVER called on `client_credentials` (verified).
- **AC-5** Net-positive-not-net-negative: the `account_type` claim + leg are unchanged (dual-read still passes); only the additive `roles` claim is new.
- **AC-6** `apps/auth-service:test` GREEN (Docker-free Mockito). Testcontainers `@SpringBootTest` IT → CI Linux.

# Related Specs

- `docs/adr/ADR-MONO-033-roles-issuance-resolution-model.md` (S3 seed, S4 locus, S5 fail-soft)
- `platform/contracts/jwt-standard-claims.md` (§ Standard Claims `roles`, § Role Strategy)
- `projects/iam-platform/specs/contracts/http/internal/account-internal-provisioning.md` (§ GET .../roles — the BE-368 edge consumed)

# Related Contracts

- `platform/contracts/jwt-standard-claims.md` § Standard Claims (`roles`) — now emitted.

# Edge Cases

- Account with no stored roles → seed by (platform, account_type).
- Operator on a platform with no seed mapping → `[]` (gateway then 403s; correct).
- account-service down → seed (fail-soft), never throw.
- No principal / no account_id (client-metadata fallback path) → no lookup; emit seed only if platform+account_type resolvable, else omit `roles`.
- `client_credentials` → no roles leg at all (a workload is not an identity; recursion guard).
- Stored roles present → emitted verbatim (NOT unioned with the seed); an explicitly-granted `OUTBOUND_MANAGER` flows through.

# Failure Scenarios

- If the roles lookup is wired on `client_credentials` → infinite recursion (the cc issuance mints the Bearer used to call account-service). Guard exactly as `populateEntitledDomains` does.
- If issuance throws on a roles-lookup failure → token minting depends on account-service availability (violates S5). Must fail-soft to seed/omit.
- If the `account_type` claim/leg is altered → breaks the dual-read tolerance (net-positive invariant). Leave account_type untouched; only ADD `roles`.
- If roles are emitted on `client_credentials` (workload) tokens → a workload is not an identity; must not carry domain roles.
