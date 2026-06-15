-- TASK-BE-386 (ADR-MONO-036 P4, M4): production data backfill — account_db half.
--
-- ADR-036 M1 wired the account-creation path to mint+assign a central identity at
-- birth (born-unified). This migration reconciles the historical gap: accounts
-- created in the window BETWEEN the V0023 identities-registry backfill and the M1
-- deploy carry `identity_id = NULL` (no creation-path writer existed yet). It mints
-- one fresh identity per such orphan and links it — the same shape V0023 applied to
-- the accounts that existed at registry-creation time.
--
-- Safety invariants (ADR-036 P4):
--   * ADDITIVE + idempotent — re-running is a no-op (no orphan ⇒ 0 rows). On a fresh
--     deploy (V0023 → M1 contiguous) there are no orphans, so this is a net-zero
--     safety net, not a destructive change.
--   * NO OVERWRITE — `WHERE a.identity_id IS NULL` only; an already-linked account is
--     never re-pointed (no silent re-link — ADR-034 § 1.3).
--   * NO EMAIL AUTO-MERGE — the identity is keyed on the account's OWN
--     (tenant_id, email); `NOT EXISTS` reuses the account's existing same-origin
--     identity if one is already present, and never folds two distinct accounts onto
--     one identity (the (tenant_id, email) unique index on accounts guarantees the
--     join below is strictly 1:1).
--   * `identity_id` stays UNMAPPED on AccountJpaEntity — this DB-level write is invisible
--     to Hibernate, preserving the value against the merge-overwrite hazard.
--
-- Cross-DB note: this migration covers ONLY account_db. The auth_db
-- (`credentials.identity_id`) and admin_db (`admin_operators.identity_id`) halves
-- cannot be reached from here (physically separate databases, no cross-DB SQL). They
-- are reconciled by the account-service-driven backfill endpoint (auth_db) and the
-- opt-in audited link surface (admin_db operators) — see the ADR-036 P4 runbook.

-- 1. Mint a fresh identity for each orphan account that does not yet have a
--    same-origin (tenant_id, email) identity in the registry. UUID() = a NEW person
--    id (NOT account.id) per ADR-034 U1-A. Timestamps inherit the account's so the
--    identity "exists since" the account did.
INSERT INTO identities (identity_id, tenant_id, primary_email, status, created_at, updated_at, version)
SELECT UUID(), a.tenant_id, a.email, 'ACTIVE', a.created_at, a.updated_at, 0
FROM accounts a
WHERE a.identity_id IS NULL
  AND NOT EXISTS (
        SELECT 1 FROM identities i
         WHERE i.tenant_id = a.tenant_id
           AND i.primary_email = a.email
      );

-- 2. Link every still-orphan account to its (tenant_id, email) identity — the one
--    just minted above, or a pre-existing same-origin identity. The join is 1:1
--    (accounts unique on (tenant_id, email); identities unique on
--    (tenant_id, primary_email)). `IS NULL` guard = no overwrite.
UPDATE accounts a
JOIN identities i
  ON i.tenant_id = a.tenant_id
 AND i.primary_email = a.email
SET a.identity_id = i.identity_id
WHERE a.identity_id IS NULL;
