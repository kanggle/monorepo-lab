# Task ID

TASK-BE-378

# Title

ADR-MONO-035 **O3 / step 4d** — add `credentials.identity_id` (the ADR-MONO-034 U6 step-3b leg re-sequenced to step 4 because it is login-path work): the third store of the central-identity correlation (`accounts.identity_id` 3a + `admin_operators.identity_id` 3c + **`credentials.identity_id` 4d**). A value-convention cross-DB reference to the central `identities` registry (account_db), mirroring `admin_operators.identity_id`. **Additive / net-zero** — nullable, unmapped on the entity, no backfill (cross-DB), no caller until a later consolidation re-keys on it.

# Status

ready

# Owner

backend

# Task Tags

- iam
- auth-service
- adr-035

---

# Dependency Markers

- **executes**: ADR-MONO-035 (ACCEPTED) **O3** + **O5 step 4d**. Completes ADR-MONO-034 **U6 step 3b** (the `credentials.identity_id` leg ADR-034 explicitly re-sequenced to step 4 because the credential row is the login anchor).
- **mirrors**: ADR-034 3a (`accounts.identity_id` — additive nullable column, deliberately UNMAPPED on the JPA entity for net-zero) + 3c (`admin_operators.identity_id` — value-convention cross-DB reference, no FK).
- **cross-DB constraint**: the central `identities` registry lives in **account_db**; auth_db cannot FK to it (cross-service FK forbidden, saas.md S1) and a single auth_db migration cannot read it — so the column is added **without** an in-migration backfill (the conceptual backfill `credentials.identity_id := accounts.identity_id via shared account_id` is a deferred consolidation step).
- **net-zero**: nullable + unmapped + no creation path wired + no caller reads it. `ddl-auto=validate` passes (validate ignores extra columns).

# Goal

`auth_db.credentials` gains a nullable `identity_id VARCHAR(36)` column referencing the central `identities` registry (account_db) by value convention (the same way `credentials.account_id` already references `accounts.id` without an FK). It is the third leg of the central-identity correlation. No creation path sets it, no caller reads it, and it is not mapped on `CredentialJpaEntity` (so no credential UPDATE ever writes/nulls it) — additive net-zero, populated by a later consolidation step.

# Scope

- **NEW migration** `projects/iam-platform/apps/auth-service/src/main/resources/db/migration/V0026__add_identity_id_to_credentials.sql` — `ALTER TABLE credentials ADD COLUMN identity_id VARCHAR(36) NULL AFTER account_id`. No FK (cross-DB), no backfill (cross-DB), no COMMENT clause (no §22 ordering concern).
- **CredentialJpaEntity**: **unchanged** — `identity_id` is deliberately NOT mapped (mirror ADR-034 3a `accounts.identity_id`), so Hibernate never writes/nulls it; `ddl-auto=validate` tolerates the extra column.
- **SPECS**: `auth-service/data-model.md` — add the `identity_id` column row (value-convention cross-DB ref, additive net-zero, unmapped, no backfill) + a migration-log V0026 entry.
- **TESTS** — `CredentialJpaRepositoryTest` (@DataJpaTest + Testcontainers, runs Flyway + `validate`): assert `identity_id` is `NULLABLE VARCHAR(36)` (INFORMATION_SCHEMA) and that a credential inserts with NULL `identity_id`, an externally-populated value round-trips (the unmapped column is never nulled by a JPA update).
- NO entity/domain/use-case change. NO contract API change (column is internal/unmapped). NO backfill.

# Acceptance Criteria

- **AC-1** After V0026, `credentials.identity_id` exists, is `VARCHAR(36)`, and is NULLABLE (INFORMATION_SCHEMA assertion).
- **AC-2** A credential inserts/round-trips with NULL `identity_id` (no creation path wired); an externally-set `identity_id` is preserved across reads (unmapped → never nulled).
- **AC-3** `CredentialJpaEntity` does not map `identity_id`; Hibernate `ddl-auto=validate` boots GREEN (the existing OAuth2 ITs + the @DataJpaTest prove validate tolerates the extra column).
- **AC-4** Net-zero: no creation path, no caller, no FK; every existing credential is unaffected (column NULL).
- **AC-5** auth-service Docker-free `:test` GREEN locally; CI `Integration (iam, Testcontainers)` is the authoritative migration/validate gate.

# Related Specs

- `docs/adr/ADR-MONO-035-operator-auth-unification-model.md` (§ O3 + § O5 4d)
- `docs/adr/ADR-MONO-034-account-credential-unification-model.md` (§ U6 step 3b — the re-sequenced leg this completes; 3a/3c the mirror pattern)
- `projects/iam-platform/specs/services/auth-service/data-model.md`

# Related Contracts

- None changed — `identity_id` is an internal, unmapped correlation column (no API surface, no claim shape change to `jwt-standard-claims.md`).

# Edge Cases

- New credential created during the window → NULL `identity_id` (no creation path wired); fine.
- A later consolidation populates `identity_id` from `accounts.identity_id` via the shared `account_id` → the unmapped column is never overwritten by a credential password-change UPDATE (mirror 3a).
- `ddl-auto=validate` on boot → passes (extra unmapped column is allowed by validate; only MISSING mapped columns fail).

# Failure Scenarios

- If `identity_id` is **mapped** on `CredentialJpaEntity` without a write-path guard → a credential UPDATE (password change / rotation) writes `identity_id=NULL`, wiping a backfilled value (the exact hazard ADR-034 3a avoided by leaving the column unmapped).
- If an FK to `identities` is added → impossible (cross-DB) and forbidden (cross-service FK, saas.md S1).
- If an in-migration backfill is attempted → impossible (auth_db cannot read account_db); the backfill is a deferred consolidation step.
- If a creation path is wired to set it now → breaks net-zero (no caller should depend on it until the consolidation).
