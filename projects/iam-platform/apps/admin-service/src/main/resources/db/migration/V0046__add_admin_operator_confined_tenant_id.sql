-- TASK-MONO-751 — `admin_operators.confined_tenant_id`: the ONE tenant an operator may assume.
--
-- Owner decision 2026-10-03 «데모 운영자는 팬 전용으로»: the demo platform operator
-- (platform-scope '*', seeded so the fan-directory screens can be shown — ADR-MONO-079 R3)
-- must assume `fan-platform` ONLY, not every registered tenant. A '*' operator otherwise
-- passes the assignment check for any tenant (OperatorAssignmentCheckUseCase step 2).
--
-- Semantics (auth-to-admin.md 판정 규칙 0 · console-registry-api.md § Tenant selection rule 5):
--   NULL     ⇒ unrestricted — every existing row, byte-unchanged behaviour (no backfill).
--   NOT NULL ⇒ the assignment check refuses every other tenant BEFORE any other step, and the
--              console registry narrows every product's `tenants` to it. It only narrows:
--              a tenant the normal rules refuse (e.g. R3's fan-platform for a customer
--              operator) stays refused.
-- Data-driven on purpose (no operator id / email in production logic); no write API — set
-- by seed/data only.
--
-- Shape: V0029 verbatim — forward-only, NULL-safe INFORMATION_SCHEMA guard, self-contained
-- PREPARE/EXECUTE/DEALLOCATE (no cross-statement user variable). MySQL-only.

SET @ddl_add_col := (
    SELECT IF(
        (SELECT COUNT(*)
           FROM information_schema.columns
          WHERE table_schema = DATABASE()
            AND table_name   = 'admin_operators'
            AND column_name  = 'confined_tenant_id') = 0,
        'ALTER TABLE admin_operators ADD COLUMN confined_tenant_id VARCHAR(32) NULL AFTER finance_default_account_id',
        'SELECT 1'
    )
);
PREPARE stmt_add_col FROM @ddl_add_col;
EXECUTE stmt_add_col;
DEALLOCATE PREPARE stmt_add_col;
