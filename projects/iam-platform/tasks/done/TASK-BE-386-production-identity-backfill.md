# Task ID

TASK-BE-386

# Title

ADR-MONO-036 **M4** (P4-B) — production cross-DB identity backfill, BUILT. Implement the P4 design (promoted from design-only): reconcile pre-existing already-split data so every principal converges on one central `identities.identity_id` across the three physically-separate IAM databases — the production analog of the M3 demo seed-rewrite. account_db via Flyway V0024 (mint+link orphan accounts); auth_db via an account-service-driven push to an auth-service batch endpoint (reusing the M2 writer); admin_db operators stay the opt-in audited link (privilege-escalation guard).

# Status

done

# Owner

backend

# Task Tags

- backend
- iam
- identity
- account-service
- auth-service
- migration

---

# Dependency Markers

- **child of**: ADR-MONO-036 (ACCEPTED 2026-06-15, TASK-MONO-266). This task is **M4 (P4-B)** — promotes the production backfill from *design-only* to **built** (see ADR § 4 Amendment).
- **depends on**: M1 (TASK-BE-381, `accounts.identity_id` writer) + M2 (TASK-BE-384, `credentials.identity_id` writer) — both MERGED in PR #1618. M4 reuses the M2 writer (`CredentialRepository.assignIdentityId`) and the identity_id columns (V0023/V0026/V0036).
- **reaffirms**: ADR-034 § 1.3 no-silent-merge — `accounts↔credentials` reconciled on the shared `account_id` (same-person certain); operators only via the opt-in audited link; never email-auto-merge.
- **NOT this task**: `account_roles` re-key to `identity_id`; dedicated identity-service; async `IdentityAssigned` writer (P3-C) — deferred follow-ups (ADR-036 § 3.3).

# Goal

Make the three IAM stores reconcilable to one central identity for pre-existing data, additively and idempotently — without wiping users, without overwriting a non-NULL identity, without email auto-merge.

# Scope

- **account_db (account-service Flyway):** `V0024__backfill_orphan_account_identities.sql` — mint a same-origin `(tenant, email)` identity per `identity_id IS NULL` account (reuse existing via `NOT EXISTS`), then link (`IS NULL` guard). Idempotent, additive, no-overwrite, no email-merge; `identity_id` stays unmapped.
- **auth_db (cross-DB, push-based):**
  - account-service: `AccountIdentityBindingReader` (application port) + `AccountIdentityBindingReaderAdapter` (native projection `findAllIdentityBindings` on `AccountJpaRepository`, cross-tenant — kept OFF the tenant-scoped `AccountRepository`); `CredentialIdentityBackfillUseCase`; `AuthServicePort.backfillCredentialIdentities(...)` + `AuthServiceClient` impl; internal endpoint `POST /internal/identity-backfill/credentials` (`IdentityBackfillController`).
  - auth-service: `BackfillCredentialIdentityUseCase` (reuses M2 `assignIdentityId`); `POST /internal/auth/credentials/identity-backfill` on `InternalCredentialController`; `BackfillCredentialIdentityRequest`/`Response`.
- **admin_db operators:** NOT auto-backfilled — opt-in audited link surface retained (documented).
- **contract:** `auth-internal.md` — add the `identity-backfill` endpoint.
- **docs:** ADR-036 § 4 Amendment (design→built); `docs/guides/adr-036-production-identity-backfill-runbook.md`.

# Acceptance Criteria

- **AC-1** After V0024, every `accounts.identity_id` is non-NULL (orphans minted+linked); re-running V0024 affects 0 rows.
- **AC-2** `POST /internal/identity-backfill/credentials` propagates each linked account's identity to `credentials.identity_id` via the M2 writer; returns `{accountsScanned, credentialsUpdated}`. Re-run ⇒ `credentialsUpdated: 0` (idempotent, no overwrite).
- **AC-3** The backfill never overwrites a non-NULL `identity_id` (both stores guard on `IS NULL`) and never email-auto-merges (keyed on the account's own `(tenant,email)` / shared `account_id`).
- **AC-4** Operator (`admin_operators.identity_id`) is NOT touched by the backfill — only the opt-in audited link sets it.
- **AC-5** Cross-tenant read uses the dedicated `AccountIdentityBindingReader`, NOT the tenant-scoped `AccountRepository` (multi-tenancy.md repository rule preserved).
- **AC-6** Net-zero on a fresh deploy (V0023→M1 contiguous ⇒ no orphans ⇒ 0 mints, 0 propagations).

# Related Specs

- `docs/adr/ADR-MONO-036-born-unified-identity-provisioning.md` (§ P4 design + § 4 Amendment — the decision this implements).
- `docs/adr/ADR-MONO-034-account-credential-unification.md` (§ 1.3 — no silent merge; opt-in operator link).
- `projects/iam-platform/docs/guides/adr-036-production-identity-backfill-runbook.md` (operational procedure).

# Related Contracts

- `projects/iam-platform/specs/contracts/http/internal/auth-internal.md` — **amended**: `POST /internal/auth/credentials/identity-backfill` (batch, idempotent).

# Edge Cases

- Account whose mint never succeeded (still `identity_id IS NULL` after V0024 — e.g. a tenant row missing) → excluded from propagation (reader returns only linked accounts); its credential stays NULL until the account is linked. Documented in the runbook.
- `credentials` row absent for an account_id → `assignIdentityId` affects 0 rows (net-zero).
- Re-run after partial transport failure → `IS NULL` guard converges; nothing partial is lost.
- Colon-verb path under class prefix → hyphen path used (`identity-backfill`) to avoid `PathPatternParser` mis-parse.

# Failure Scenarios

- If V0024 minted before checking `NOT EXISTS` → duplicate identities per `(tenant,email)` → unique-index violation; avoided by the `NOT EXISTS` guard + 1:1 join.
- If the propagation overwrote a non-NULL value → silent re-link; prevented by the `IS NULL` guard (both stores).
- If the cross-tenant read were added to `AccountRepository` → violates the tenant-first repository rule (cross-tenant leak surface); isolated to a dedicated platform-level port instead.
- If auth-service is unavailable mid-sweep → `AuthServiceUnavailable`; nothing partial persists incorrectly (idempotent re-run converges).
