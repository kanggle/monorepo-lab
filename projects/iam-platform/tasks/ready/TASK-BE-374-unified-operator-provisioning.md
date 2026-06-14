# Task ID

TASK-BE-374

# Title

Unified new-operator provisioning — operator creation resolves/creates a central identity + sets `identity_id` (ADR-MONO-034 U6 **step 3d** / U4 / ADR-MONO-032 D5 step 3). Stops the operator↔identity divergence at the source: every operator provisioned after step 3 is linked to a central identity (created fresh, or reused only with **explicit opt-in** — never silently merged). The final step of ADR-034's link-first execution.

# Status

ready

# Owner

backend-engineer

# Task Tags

- backend
- iam
- account-service
- admin-service
- security

---

# Dependency Markers

- **child of**: ADR-MONO-034 (ACCEPTED). Fourth/final execution step (U6 step 3d) of the § 3.3 roadmap.
- **depends on**: BE-371 (3a registry) + BE-372 (3b resolve EP) + BE-373 (3c `admin_operators.identity_id` + `linkIdentity` port). All in `main`.
- **completes**: the ADR-034 link-first scope (3a registry → 3b resolve → 3c explicit link → 3d provisioning auto-link). Remaining ADR-032 work is **step 4** (drop legacy `account_type` + operator login/credential consolidation + `credentials.identity_id` + `password_hash` removal) → **step 5** (e2e).
- **no-silent-merge** (U3 / U7): an existing identity for (tenant, email) is REUSED only when the caller opts in (`reuseExisting`); otherwise the operator is created UNLINKED (explicit linking remains the 3c surface). `uk_identities_tenant_email` forbids a duplicate identity, so this opt-in is the safe resolution.
- **keeps disjoint** (U5): only identity creation + `admin_operators.identity_id`. `account_roles` / `admin_operator_roles` / token issuance / `jwt-standard-claims.md` unchanged. No Flyway migration (registry from 3a, column from 3c).

# Goal

Make new operators automatically carry a central identity, so the registry stays complete for operators going forward — without ever silently merging two identities, and without provisioning hard-failing on identity-infra unavailability.

# Scope

- **account-service** — `POST /internal/tenants/{tenantId}/identities:resolveOrCreate` (`ResolveOrCreateIdentityController`, AIP-136 colon-verb, `X-Tenant-Id` defense-in-depth) body `{email, reuseExisting}` → `{identityId|null, outcome: CREATED|REUSED|EXISTS_NOT_REUSED}`. `ResolveOrCreateIdentityUseCase` (uses 3a `IdentityRepository`): not-exists→create CREATED; exists&reuse→REUSED; exists&!reuse→`identityId:null` EXISTS_NOT_REUSED (no mutation/merge); race (`uk_identities_tenant_email`)→catch `DataIntegrityViolationException`+re-read. DTOs + contract (`account-internal-provisioning.md`). No audit/outbox (provisioning primitive, U5).
- **admin-service** — `CreateOperatorRequest.reuseExistingIdentity` (nullable→false); `AccountServiceClient.resolveOrCreateIdentity(tenant, email, reuse)` (**fail-soft** → null on `DownstreamFailureException`); `CreateOperatorUseCase` after create+roles: skip `'*'` (no account_db tenant), else resolve-or-create (fail-soft) + `linkIdentity` (3c) when non-null; backward-compat overloads default `reuseExisting=false`. Contract (`admin-api.md` `POST /api/admin/operators`).
- Tests: `ResolveOrCreateIdentityUseCaseTest` + `ResolveOrCreateIdentityControllerTest` (Docker-free) + `CreateOperatorUseCaseTest` extensions (created+linked / `'*'` skip / fail-soft no-link / reuse passthrough) + `IdentityJpaRepositoryTest` EP-behavior IT (create + unique + reuse; CI authoritative).

# Acceptance Criteria

- **AC-1** New operator (non-`'*'` tenant, fresh email) → a central identity is CREATED and `admin_operators.identity_id` is set.
- **AC-2** Existing identity for (tenant, email): `reuseExisting=true` → REUSED + linked; `reuseExisting=false` → operator created UNLINKED (no merge, no error). No silent merge.
- **AC-3** `'*'` platform-scope operator → identity resolve/link SKIPPED (no account_db tenant); operator created unlinked.
- **AC-4** Fail-soft: account-service unavailable/errors → operator still created, `identity_id` null (the OPPOSITE of the 3c fail-closed link).
- **AC-5** Race: concurrent resolve-or-create for the same (tenant, email) converge on one identity (catch `DataIntegrityViolationException` + re-read); no constraint-violation escapes.
- **AC-6** U5: no `account_roles` / `admin_operator_roles` / token-issuance / `jwt-standard-claims.md` change; no Flyway migration; no audit/outbox in the resolve-or-create EP.
- **AC-7** Docker-free unit GREEN (both modules' `application.*` + `presentation.*`); Testcontainers IT GREEN on CI.

# Related Specs

- `docs/adr/ADR-MONO-034-account-credential-unification-model.md` (U4, U3, U6 step 3d)
- `projects/iam-platform/specs/contracts/http/internal/account-internal-provisioning.md`
- `projects/iam-platform/specs/contracts/http/admin-api.md`

# Related Contracts

- `account-internal-provisioning.md` — new resolve-or-create EP (documented here).
- `admin-api.md` — `reuseExistingIdentity` + identity-link side effect on `POST /api/admin/operators`.

# Edge Cases

- `'*'` operator → skip (no FK anchor).
- account-service down → fail-soft (operator created unlinked).
- concurrent same (tenant,email) → race re-read (one identity).
- existing identity + no opt-in → unlinked (link later via 3c); `uk_identities_tenant_email` forbids a duplicate so a fresh-create is not an option.

# Failure Scenarios

- Silent reuse on email match (no opt-in) → re-introduces the § 1.3 merge vector; FORBIDDEN — default `reuseExisting=false` leaves unlinked.
- Fail-closed on downstream → provisioning would hard-fail on identity infra; must be fail-soft (operator creation is primary).
- Creating an identity for a `'*'` operator → identities FK has no `'*'` tenant; must skip.
- New Flyway migration → none needed; registry (3a) + column (3c) already exist.
