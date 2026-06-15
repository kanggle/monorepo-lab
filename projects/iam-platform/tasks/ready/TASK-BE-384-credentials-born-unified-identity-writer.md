# Task ID

TASK-BE-384

# Title

ADR-MONO-036 **M2** — born-unified `credentials.identity_id` writer (completes ADR-MONO-035 **O3**). Propagate the central `identity_id` minted at account creation (M1) **in-band** to auth-service credential creation, and write it to the new credential row from birth (native, idempotent, no overwrite). Closes the second of three stores left born-split (after M1's `accounts.identity_id`), so a new consumer registration is born linked on `accounts` + `credentials` to the same central identity.

# Status

ready

# Owner

backend

# Task Tags

- backend
- iam
- account-service
- auth-service
- identity

---

# Dependency Markers

- **child of**: ADR-MONO-036 (ACCEPTED 2026-06-15, TASK-MONO-266) — born-unified identity provisioning. This task is **M2** (P3): the `credentials.identity_id` writer.
- **stacked on**: TASK-BE-381 (M1) — reuses M1's `AccountIdentityProvisioner` mint; the use cases now capture the minted `identityId` and propagate it to `createCredential`.
- **completes**: ADR-MONO-035 O3 — the `credentials.identity_id` shadow column (V0026, BE-378) finally gets a production writer.
- **reaffirms**: ADR-034 § 1.3 no-silent-merge — in-band propagation of a same-origin `(tenant,email)` identity, NOT email auto-merge; `IS NULL` guard = no overwrite.
- **followed by**: M3 (seed-rewrite, P4). Production cross-DB backfill = designed-deferred (ADR-036 P4).

# Goal

Make a new consumer credential born linked to the same central identity as its account: propagate the M1-minted `identity_id` from account-service to auth-service credential creation and write it to `credentials.identity_id` at creation — without blocking registration (net-zero when absent).

# Scope

- **account-service**: `AuthServicePort.createCredential(...)` += `identityId` param; `AuthServiceClient` sends it in the request body (omitted when null); `SignupUseCase` / `ProvisionAccountUseCase` capture the minted `identityId` (M1 helper now returns it) and pass it to `createCredential`.
- **auth-service**: `CreateCredentialRequest` / `CreateCredentialCommand` += optional `identityId` (backward-compat 3-arg/4-arg ctors retained); `InternalCredentialController` threads it; `CredentialJpaRepository.assignIdentityIdIfAbsent` (native `@Modifying`, `identity_id` UNMAPPED — merge-overwrite hazard, `AND identity_id IS NULL`, flush/clearAutomatically); `CredentialRepository.assignIdentityId` (port+impl); `CreateCredentialUseCase` writes it on the create + idempotent-retry + race paths via `linkIdentityIfPresent` (net-zero when null).
- **contract**: `specs/contracts/http/internal/auth-internal.md` — add optional `identityId` field to the createCredential request.
- Tests: account-service use case + AuthServiceClient (body carries identityId); auth-service `CreateCredentialUseCaseTest` (propagation + null skip) + `CredentialJpaRepositoryTest` native writer (Testcontainers, CI-authoritative).

# Acceptance Criteria

- **AC-1** A new credential created via Signup / ProvisionAccount carries `credentials.identity_id` = the account's central identity (the same value M1 wrote to `accounts.identity_id`) — born linked on both stores.
- **AC-2** When the account-side mint failed (M1 fail-soft → `identityId` null), the credential is born unlinked (`identity_id` NULL); registration still succeeds (net-zero). No new failure mode on the login-critical credential path.
- **AC-3** `assignIdentityIdIfAbsent` is idempotent and never overwrites (`IS NULL` guard): a second assign with a different identity is a 0-row no-op; missing account → 0 rows.
- **AC-4** `identity_id` stays UNMAPPED on `CredentialJpaEntity` (native write) — no Hibernate merge-overwrite on a credential update (mirror ADR-034 3a / ADR-035 O3).
- **AC-5** In-band propagation only (P3-A) — no synchronous auth→account call added to the login path; no async event.
- **AC-6** Contract updated (optional `identityId`); backward-compatible (absent → unchanged behavior).

# Related Specs

- `docs/adr/ADR-MONO-036-born-unified-identity-provisioning.md` (§ P3 — the decision this implements)
- `docs/adr/ADR-MONO-035-operator-auth-unification-model.md` (§ O3 — the `credentials.identity_id` column this writer completes)
- `projects/iam-platform/specs/services/auth-service/architecture.md` (the credential creation path)

# Related Contracts

- `projects/iam-platform/specs/contracts/http/internal/auth-internal.md` — **amended**: optional `identityId` on `POST /internal/auth/credentials` (backward-compatible).

# Edge Cases

- Credential INSERT not yet flushed when the native UPDATE runs → `@Modifying(flushAutomatically=true)` flushes first; `clearAutomatically=true` evicts stale state.
- Idempotent retry (half-commit recovery) → `linkIdentityIfPresent` backfills the identity on the existing row if absent (idempotent).
- Race recovery (DataIntegrityViolation → existing row) → the writer is also invoked there.
- `credentials.identity_id` has NO FK (value-convention cross-DB ref, V0026) — any UUID is writable; no `identities` row required in auth_db.

# Failure Scenarios

- If `identity_id` were mapped on `CredentialJpaEntity`, a later password-change update could let Hibernate overwrite/null it → native write keeps it unmapped.
- If the writer overwrote a non-NULL value → silent re-link; `AND identity_id IS NULL` prevents it.
- If propagation used a synchronous auth→account resolve call → a new dependency on the login-critical path; ADR-036 P3-A uses in-band propagation instead.
- If the credential write hard-failed when `identityId` is null → registration regression; the writer is net-zero (skipped) when null.
