# Task ID

TASK-MONO-263

# Title

ADR-MONO-035 **4b-2b** (ADR-032 D5 step 4) — stop emitting the `account_type` claim, drop the `auth_db.credentials.account_type` column, decouple `RoleSeedPolicy` from `account_type`, and finalize the contract. This is the core of the `account_type` removal: after 4b-1 (web-store role-based guard) and 4b-2a (gateways roles-only), nothing consumes `account_type`, so the IdP stops emitting it and the column is dropped. Consumers keep `CUSTOMER` (seed by platform); operators get domain roles at assume-tenant (4a) — neither needs `account_type`.

# Status

ready

# Owner

backend

# Task Tags

- iam
- auth-service
- account-service
- security
- adr-035

---

# Dependency Markers

- **executes**: ADR-MONO-035 O5 step 4b (stop emit + decouple seed + drop column + finalize contract) / ADR-032 D5 step 4 (remove the `account_type` claim + column).
- **MUST follow**: 4b-1 (MONO-261, merged — web-store role-based guard; else stopping emission no-ops the guard → operator storefront admit). 4b-2a (MONO-262, gateways roles-only) is independent (the `account_type` leg is inert either way) but landing it first leaves no inert leg.
- **finalizes**: `platform/contracts/jwt-standard-claims.md` `account_type` (Deprecated → removed) per its § Change Rule — the contract body was prepared at step 0 (MONO-255).
- **atomic seed cleanup**: every `INSERT INTO credentials (... account_type ...)` must drop the `account_type` column in the same change (the column is gone) — else Flyway-seeded ITs / nightly federation e2e / frontend e2e break.

# Goal

The IdP emits no `account_type` claim; `auth_db.credentials` has no `account_type` column; `RoleSeedPolicy` seeds consumer roles by platform only; the credential-provisioning API + contract carry no `accountType`. Consumers carry `CUSTOMER` (seed); operators carry domain roles at assume-tenant (4a).

# Scope

### auth-service (issuance + credential store)
- `TenantClaimTokenCustomizer`: remove `CLAIM_ACCOUNT_TYPE` + `injectAccountType`/`injectAccountTypeFromPrincipal` + their calls (auth-code path + assume-tenant path). `populateRoles`: stop reading `account_type` from the principal; call `RoleSeedPolicy.seed(platform)` (consumer-only). `customizeForAssumeTenant`: remove the `operatorAccountType` extraction + injection.
- `RoleSeedPolicy`: drop the `accountType` param → `seed(platformTenantId)` returns the **consumer** role by platform (`ecommerce → CUSTOMER`, `fan-platform → FAN`, else `[]`). Remove the OPERATOR branch entirely (operators are seeded at assume-tenant by `OperatorRoleDerivation`, BE-376, not at base login). Update `RoleSeedPolicyTest`.
- `CredentialAuthenticationProvider`: remove the `account_type` read + `details.put("account_type", …)`.
- `Credential` (domain): remove `accountType` field, `ACCOUNT_TYPE_*`/`DEFAULT_ACCOUNT_TYPE` constants, `normalizeAccountType`, and the `create(...)` overload carrying `accountType` (keep a single `create`). `CredentialJpaEntity`: remove the `account_type` `@Column` + `toDomain`/`fromDomain` handling.
- `CreateCredentialCommand` / `CreateCredentialUseCase` / `CreateCredentialRequest` (drop the `@Pattern` field) / `InternalCredentialController`: remove `accountType`.
- `AssumeTenantAuthenticationProvider` + `AssumeTenantAuthenticationToken`: remove the `operatorAccountType` plumbing (subject-token read + grant field). Keep `orgScope` + the BE-376 role derivation intact.
- **Migration** `V0025__drop_account_type_from_credentials.sql`: `ALTER TABLE credentials DROP COLUMN account_type;` (simple DROP — no COMMENT/AFTER ordering issue, §22).

### account-service (provisioning caller)
- `AuthServicePort`: remove `ACCOUNT_TYPE_*` constants + the `accountType` param. `AuthServiceClient`: remove `accountType` from the POST body. `SignupUseCase` (was CONSUMER) + `ProvisionAccountUseCase` (was OPERATOR): drop the `accountType` arg.

### contracts
- `platform/contracts/jwt-standard-claims.md`: `account_type` Deprecated → **removed** (finalize per § Change Rule). Update § Gateway Enforcement / § SSO / `X-Account-Type` to the role-based end state.
- `projects/iam-platform/specs/contracts/http/internal/auth-internal.md`: remove `accountType` from `POST /internal/auth/credentials`.

### seed SQLs that INSERT credentials.account_type (drop the column from each — atomic)
Grep `account_type` across migrations + dev-seeds + e2e fixtures + scripts and remove the column from every `credentials` INSERT (the column will not exist):
- `projects/iam-platform/apps/auth-service/src/main/resources/db/migration*/` (any dev-seed inserting credentials with account_type) — and DO NOT leave V0022 referencing a column that V0025 drops in a way that breaks (V0022 ADD then V0025 DROP is fine; verify no later migration reads it).
- `tests/federation-hardening-e2e/fixtures/seed.sql` (4 operator INSERTs), `scripts/console-demo/seed/01-iam.sql`, `projects/platform-console/apps/console-web/tests/e2e/fixtures/seed.sql`, `projects/ecommerce-microservices-platform/apps/web-store/e2e/fixtures/iam-consumer-seed.sql` — remove `account_type` from the column list + values.

### tests
- Update all auth-service + account-service tests asserting `account_type` (TenantClaimTokenCustomizerTest, CredentialAuthenticationProviderTest, CredentialTest, CredentialJpaRepositoryTest, InternalCredentialControllerTest, FormLoginIntegrationTest, RoleSeedPolicyTest, AssumeTenantAuthenticationProviderTest, CreateCredentialUseCaseTest, SignupUseCaseTest, ProvisionAccountUseCaseTest, TenantProvisioningIntegrationTest, AuthServiceClientUnitTest).

# Acceptance Criteria

- **AC-1** No token carries `account_type` (customizer emits none on any grant). Verified by TenantClaimTokenCustomizerTest (the prior account_type assertions removed/inverted).
- **AC-2** `credentials` has no `account_type` column (V0025 drop); `CredentialJpaEntity` maps no such column; the iam Testcontainers IT (Flyway validate + form-login) is green.
- **AC-3** `RoleSeedPolicy.seed(platform)` returns `CUSTOMER` for ecommerce, `FAN` for fan-platform, `[]` otherwise; consumers' auth-code tokens still carry `CUSTOMER` (FormLoginIntegrationTest).
- **AC-4** Operators' assumed tokens still carry domain roles (4a unaffected); the assume-tenant path no longer injects `account_type`.
- **AC-5** The credential-provisioning API + `auth-internal.md` + `jwt-standard-claims.md` carry no `account_type`/`accountType`; the contract is finalized (Deprecated → removed).
- **AC-6** Every `credentials` seed INSERT drops `account_type` (no Flyway/IT/e2e seed references the removed column).
- **AC-7** auth-service + account-service Docker-free `:test` green; CI `Integration (iam, Testcontainers)` is the authoritative gate (Flyway V0025 + form-login + roles wiring).

# Related Specs / Contracts

- `docs/adr/ADR-MONO-035-...` (§ O5 4b), `docs/adr/ADR-MONO-032-...` (§ D5 step 4), `docs/adr/ADR-MONO-033-...` (§ S3 seed — now platform-only)
- `platform/contracts/jwt-standard-claims.md` (finalized) + `projects/iam-platform/specs/contracts/http/internal/auth-internal.md`

# Edge Cases

- §22: the migration is a simple `DROP COLUMN` (no COMMENT/AFTER); verify no later migration or `@Column` references `account_type` after the drop (Flyway `validate` + JPA `validate` would fail).
- A consumer on a non-`ecommerce`/`fan-platform` platform → seed `[]` → relies on stored `account_roles` (unchanged from ADR-033); the operator base login (platform=`gap`) seeds `[]` (correct — operators get roles at assume-tenant).
- Seed INSERTs: a missing column in the INSERT column-list is required (the column is gone); leaving `account_type` in any INSERT → Flyway/seed failure.

# Failure Scenarios

- If a `credentials` seed still inserts `account_type` after the column is dropped → Flyway/IT/e2e RED (atomic cleanup required).
- If `RoleSeedPolicy` is dropped entirely (not just the account_type param) → consumers lose `CUSTOMER` seed → storefront 403; keep the platform→consumer-role seed.
- If this lands before 4b-1 → the web-store guard no-ops (operator storefront admit); 4b-1 is merged, so order is satisfied.
- If `CredentialJpaEntity` still maps `account_type` after V0025 → JPA `validate` fails on boot (iam IT RED).
