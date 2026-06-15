# Task ID

TASK-BE-381

# Title

ADR-MONO-036 **M1** — born-unified identity provisioning on the consumer account-creation paths. Mint (or reuse) the central `identities` row at account creation and assign `accounts.identity_id` from birth, so new consumer accounts are born linked to a central identity (ADR-032 D6-A for new records) instead of relying on an after-the-fact link. Fail-soft (REQUIRES_NEW isolation) so registration never blocks on the identity infrastructure (P2).

# Status

ready

# Owner

backend

# Task Tags

- backend
- iam
- account-service
- identity

---

# Dependency Markers

- **child of**: ADR-MONO-036 (ACCEPTED 2026-06-15, TASK-MONO-266) — born-unified identity provisioning. This task is **M1** (P1/P2): the consumer-registration half of "born unified."
- **generalizes**: ADR-MONO-034 U4 (`ResolveOrCreateIdentityUseCase` at operator creation) to the consumer account-creation paths.
- **followed by**: M2 (TASK-BE-384, P3 — `credentials.identity_id` writer; renumbered from BE-382 to avoid collision with the merged ecommerce TASK-BE-382) + M3 (TASK-MONO-268, seed-rewrite, P4). Production cross-DB backfill = designed-deferred (ADR-036 P4).
- **reaffirms**: ADR-034 § 1.3 no-silent-merge — convergence is same-origin issuance keyed on (tenant, email), `reuseExisting=true`, NOT email auto-merge.

# Goal

Make new consumer accounts born linked to a central identity: at account creation, mint/reuse the `identities` row and set `accounts.identity_id` — without ever blocking registration on the identity infrastructure (fail-soft).

# Scope

- `AccountIdentityProvisioner` (NEW) — `@Transactional(REQUIRES_NEW)` wrapper over `ResolveOrCreateIdentityUseCase` (reuseExisting=true). REQUIRES_NEW isolates a mint failure (incl. the inner use case's rollback-only mark) from the caller's registration tx; the caller fail-softs on any exception.
- `AccountJpaRepository.assignIdentityIdIfAbsent` (NEW) — native `@Modifying` UPDATE (`identity_id` stays UNMAPPED on the entity — the merge-overwrite hazard), `AND identity_id IS NULL` (idempotent, no overwrite), `flushAutomatically/clearAutomatically=true`.
- `AccountRepository.assignIdentityId` (port) + `AccountRepositoryImpl` (impl).
- `SignupUseCase` / `SocialSignupUseCase` / `ProvisionAccountUseCase` — after `Account.create` + save: mint fail-soft → if non-null, `assignIdentityId`. Order = mint (creates `identities` row, satisfies FK `fk_accounts_identity_id`) THEN assign.
- Tests: unit (born-unified mint+assign happy path + fail-soft) on the three use cases; `AccountJpaRepositoryTest` native-writer test (Testcontainers; CI-authoritative, skipped locally per Docker regression).

# Acceptance Criteria

- **AC-1** A new consumer account created via Signup / SocialSignup / ProvisionAccount has `accounts.identity_id` set to a central `identities` row at creation (happy path).
- **AC-2** When an identity already exists for (tenant, email) — e.g. an operator was provisioned first — it is REUSED (`reuseExisting=true`): consumer and operator converge on the SAME identity (no merge).
- **AC-3** Fail-soft: if the mint fails (identity infra unavailable / inner tx rollback-only), registration STILL succeeds (account + credential + event), the account is born unlinked (`identity_id` NULL), and no exception escapes registration. The REQUIRES_NEW boundary guarantees the registration tx is not poisoned.
- **AC-4** `assignIdentityIdIfAbsent` is idempotent and never overwrites: a second assign with a different identity is a 0-row no-op; the original value is preserved. Tenant-scoped (cross-tenant/missing → 0 rows).
- **AC-5** `identity_id` stays UNMAPPED on `AccountJpaEntity` (native read + native write) — no Hibernate merge-overwrite of the column on account updates.
- **AC-6** No authorization / role change (identity correlation only). No contract change.

# Related Specs

- `docs/adr/ADR-MONO-036-born-unified-identity-provisioning.md` (P1/P2/P3 — the decision this implements)
- `docs/adr/ADR-MONO-034-account-credential-unification-model.md` (§ U4 the pattern generalized + § 1.3 no-silent-merge)
- `projects/iam-platform/specs/services/account-service/architecture.md` (the registration paths + the `identities` registry)

# Related Contracts

- None amended — identity correlation is additive net-zero; no wire-shape change.

# Edge Cases

- Mint succeeds but the outer registration tx later rolls back (e.g. credential 409) → the `identities` row (committed in REQUIRES_NEW) persists as a benign orphan, reused on retry (idempotent on `uk_identities_tenant_email`).
- Account row not yet flushed when the native UPDATE runs → `@Modifying(flushAutomatically=true)` flushes the pending INSERT first; `clearAutomatically=true` evicts stale entity state after.
- FK `fk_accounts_identity_id`: the mint creates the `identities` row BEFORE the assign UPDATE references it — order is mint-then-assign.
- Concurrent same-email signup race → existing `existsByEmail` pre-check + DataIntegrityViolation catch unchanged; the assign flush may surface the race earlier but it is caught by the same handler.

# Failure Scenarios

- If the mint were called in the SAME transaction (not REQUIRES_NEW), an inner rollback-only mark would poison the registration tx → registration would abort on identity-infra downtime (the P2 regression). This task isolates the mint in REQUIRES_NEW.
- If `identity_id` were mapped on `AccountJpaEntity`, a later account update would let Hibernate overwrite/null the column (the merge-overwrite hazard) → native read + native write keep it unmapped.
- If the assign overwrote a non-NULL value → silent re-link; `AND identity_id IS NULL` prevents it (idempotent, net-zero).
- If convergence used email auto-merge instead of (tenant,email) same-origin issuance → reopens the ADR-034 § 1.3 cross-tenant email-collision vector; `reuseExisting` on the registration's own (tenant,email) is same-origin, not a merge.
