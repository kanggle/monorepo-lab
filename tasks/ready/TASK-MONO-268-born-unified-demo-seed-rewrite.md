# Task ID

TASK-MONO-268

# Title

ADR-MONO-036 **M3** — born-unified demo seed rewrite (§ P4). Rewrite the federation-hardening e2e runtime seed so the seeded customer-tenant operator principals are born-unified: a real central `identities` row per operator-person, with the SAME `identity_id` written onto every IAM store it occupies (`accounts` where it is also a consumer, `auth_db.credentials`, `admin_db.admin_operators`). Makes the demo data look exactly as if it had been provisioned through the M1/M2 unified path — closing the born-SPLIT state that motivated ADR-036.

# Status

ready

# Owner

backend

# Task Tags

- monorepo
- iam
- identity
- e2e
- seed

---

# Dependency Markers

- **child of**: ADR-MONO-036 (ACCEPTED 2026-06-15, TASK-MONO-266) — born-unified identity provisioning. This task is **M3** (P4): the demo seed-rewrite. Final piece of the ADR roadmap (§ 3.3).
- **completes (forward demo)**: M1 (TASK-BE-381, `accounts.identity_id` writer) + M2 (`credentials.identity_id` writer) made NEW registrations born-unified at runtime; M3 makes the pre-seeded demo principals born-unified too, so the live demo shows the 3-store match WITHOUT a runtime link call.
- **NOT this task**: production cross-DB reconciliation backfill = DESIGN-only (ADR-036 P4 — real users cannot be wiped; the opt-in audited `…/identity:link` surface stays the reconciliation tool for pre-existing split data).
- **depends on schema (all present on main)**: account_db `identities` registry + `accounts.identity_id` (V0023), `auth_db.credentials.identity_id` (V0026), `admin_db.admin_operators.identity_id` (V0036). M3 is SEED-only — it does not depend on the M1/M2 application code compiling.

# Goal

Seed the federation-hardening demo so every customer-tenant operator-person is born-unified to one central identity across all stores it occupies, and the headline persona (`multi-operator`) is a TRUE consumer+operator co-holder (accounts + credentials + admin_operators all carrying the same `identity_id`).

# Scope

- `tests/federation-hardening-e2e/fixtures/seed.sql` — ADD-ONLY born-unified block (M3.1/M3.2/M3.3):
  - **M3.1 account_db**: INSERT IGNORE one `identities` row per customer-tenant operator (`acme-corp-operator`, `multi-operator`, `tenant-admin-umbrella`, `tenant-billing-admin-umbrella`); INSERT IGNORE the `multi-operator` consumer `accounts` row (id == its credential `account_id` `…c300`, `identity_id` = `…d300`) — the full 3-store co-holder.
  - **M3.2 auth_db**: `UPDATE credentials SET identity_id = … WHERE account_id = … AND identity_id IS NULL` (4 principals) — the seed analog of the M2 native writer (idempotent, no overwrite).
  - **M3.3 admin_db**: `UPDATE admin_operators SET identity_id = … WHERE operator_id = … AND identity_id IS NULL` (4 principals) — the seed analog of the U3 audited link.
- **Deliberately out of scope** (documented in the seed block): `e2e-super-admin` and any `tenant_id='*'` operator stay born-UNLINKED — `'*'` is the platform-scope sentinel, NOT a row in account_db `tenants`, so an `identities` row (FK → tenants) is impossible; a platform super-admin is not a customer-tenant person. This is the net-zero "born unlinked" outcome (mirrors M1/M2 fail-soft), not a gap.
- **Separate harness, deferred**: the platform-console `console-web` e2e seed (`tests/e2e/fixtures/seed.sql`) seeds only `e2e-super-admin` (`'*'`, unlinkable) + a credential-less target operator — low born-unified value + account_db apply-order uncertainty → left unchanged.

# Acceptance Criteria

- **AC-1** After the federation-e2e seed runs, `account_db.identities` holds the 4 customer-tenant operator identities (was EMPTY); `account_db.accounts` holds the `multi-operator` consumer row with `identity_id = '…d300'`.
- **AC-2** `auth_db.credentials.identity_id` is non-NULL and equal to the matching identity for the 4 principals (`…c200→…d200`, `…c300→…d300`, `…c401→…d401`, `…c402→…d402`).
- **AC-3** `admin_db.admin_operators.identity_id` equals the SAME identity for the 4 principals — so the live "3-store match" demo (`multi-operator`: accounts == credentials == admin_operators) holds from seed, no `…/identity:link` call required.
- **AC-4** `e2e-super-admin` (and any `'*'` operator) carries `identity_id` NULL across all stores — documented born-unlinked net-zero.
- **AC-5** The block is idempotent / re-runnable: a second application is a no-op (INSERT IGNORE + `IS NULL`-guarded UPDATEs); a stray different `identity_id` is never overwritten.
- **AC-6** Net-zero for every existing federation-e2e spec — the block only ADDS rows/sets a previously-NULL column the specs do not assert on; no SUPER_ADMIN/acme/multi/umbrella row above is mutated except the additive `identity_id`.

# Related Specs

- `docs/adr/ADR-MONO-036-born-unified-identity-provisioning.md` (§ P4 — the decision this implements; seed-rewrite for demo, design-only for production).
- `docs/adr/ADR-MONO-034-account-credential-unification.md` (§ 1.3 — same-origin, no silent email merge: every identity is keyed by the operator's own (tenant, email)).
- `docs/adr/ADR-MONO-032-unified-identity-roles.md` (the one-account-one-identity / consumer+operator co-holding model M3 demonstrates).

# Related Contracts

- None amended. M3 is a runtime data fixture (not a Flyway migration, not an API). The columns it writes are already specified (V0023/V0026/V0036 + ADR-034/035/036).

# Edge Cases

- Seed applied AFTER GAP/account-service Flyway (phase 1.5) → `identities` table + tenant rows (acme-corp V0020, umbrella-corp V9003) exist; the FK `fk_identities_tenant_id` and `fk_accounts_identity_id` resolve.
- `accounts` insert ordered AFTER its `identities` insert (FK `fk_accounts_identity_id`).
- `accounts.email` uniqueness is per-tenant `(tenant_id, email)` (V0010) — `multi-operator@example.com` under `acme-corp` is unique; no collision with any consumer.
- Re-run: identities/accounts INSERT IGNORE; identity_id UPDATEs guard on `IS NULL` → 0-row no-op, no overwrite.
- A `'*'`-tenant operator → no identities row possible (FK) → intentionally left NULL (born unlinked).

# Failure Scenarios

- If an `identities` row were inserted for a `'*'` operator → FK violation (no `'*'` tenant) → seed abort; avoided by scoping born-unify to real customer tenants only.
- If the `accounts` row were inserted before its `identities` row → FK `fk_accounts_identity_id` violation; avoided by M3.1 ordering (identities first).
- If the identity_id UPDATEs omitted the `IS NULL` guard → a re-run (or a prior link) could be silently overwritten; the guard makes it idempotent and overwrite-safe (mirrors the M1/M2 writers).
- If the block mutated existing operator/credential rows beyond `identity_id` → federation-e2e regression; it is strictly additive (new rows + a previously-NULL column).
