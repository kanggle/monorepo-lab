-- TASK-BE-298 / ADR-MONO-014 (ACCEPTED) § D2/D3
-- Add admin_operators.oidc_subject — the OIDC subject <-> operator link key
-- for POST /api/admin/auth/token-exchange (GAP OIDC platform-console-web
-- access token -> operator token). Link-key sub-decision + rationale:
-- specs/services/admin-service/data-model.md §OIDC Subject <-> Operator Link Key.
--
-- ------------------------------------------------------------------------
-- Shape contract (TASK-BE-297 V0016 cycle-3 lesson applied verbatim)
-- ------------------------------------------------------------------------
-- This is a STRUCTURAL DDL migration (ALTER TABLE ... ADD COLUMN /
-- ADD UNIQUE INDEX), NOT a text/JSON value rewrite, so the V0016 JSON
-- normalization no-op trap does not apply here. The relevant V0016 cycle-3
-- discipline that DOES apply and is honored:
--
--   1. FORWARD-ONLY. No down/rollback statement (data-model.md migration
--      policy). PII/secret column policy is unaffected (oidc_subject is a
--      non-PII opaque OIDC `sub` UUID, classified `internal`).
--
--   2. NO cross-statement MySQL user variable. The V0016 cycle-2 no-op was
--      caused by `SET @x := ...` referenced across Flyway-split statements
--      resolving to NULL. Every statement below is wholly SELF-CONTAINED:
--      the idempotency guard, the dynamic-SQL string, and the EXECUTE are a
--      single PREPARE/EXECUTE/DEALLOCATE triple that derives its branch
--      purely from INFORMATION_SCHEMA at execution time. No `@var` is read
--      in a later statement than it was set; the only session variables used
--      (@ddl_*) are written and consumed inside the SAME prepared block.
--
--   3. NULL-SAFE idempotency guard. The V0016 cycle-2 no-op also came from a
--      NULL-unsafe predicate (`NULL <> 'x'` is NULL, not TRUE, silently
--      dropping every row). Here the guard is
--      `(SELECT COUNT(*) FROM information_schema.columns WHERE ...) = 0`
--      and `... statistics ... ) = 0` — COUNT(*) is ALWAYS an integer
--      (never NULL), so `= 0` always yields TRUE or FALSE, never NULL. The
--      migration is therefore safely re-runnable: if the column / index
--      already exists the COUNT is > 0 and the DDL string becomes a
--      harmless `SELECT 1` no-op.
--
-- MySQL-only (`information_schema`, PREPARE/EXECUTE) is deliberate and
-- correct: admin-service runs ONLY MySQL 8.0 (architecture.md "퍼시스턴스:
-- MySQL"; every Flyway-enabled test uses MySQLContainer("mysql:8.0")).

-- ------------------------------------------------------------------------
-- 1. Add the oidc_subject column (idempotent, self-contained).
-- ------------------------------------------------------------------------
SET @ddl_add_col := (
    SELECT IF(
        (SELECT COUNT(*)
           FROM information_schema.columns
          WHERE table_schema = DATABASE()
            AND table_name   = 'admin_operators'
            AND column_name  = 'oidc_subject') = 0,
        'ALTER TABLE admin_operators ADD COLUMN oidc_subject VARCHAR(255) NULL AFTER totp_enrolled_at',
        'SELECT 1'
    )
);
PREPARE stmt_add_col FROM @ddl_add_col;
EXECUTE stmt_add_col;
DEALLOCATE PREPARE stmt_add_col;

-- ------------------------------------------------------------------------
-- 2. Add the platform-global UNIQUE index (idempotent, self-contained).
--    MySQL UNIQUE allows multiple NULLs, so non-provisioned operators
--    (oidc_subject IS NULL) are unconstrained; provisioned values are
--    globally unique (OIDC subject space is tenant-independent —
--    data-model.md §OIDC Subject <-> Operator Link Key).
-- ------------------------------------------------------------------------
SET @ddl_add_idx := (
    SELECT IF(
        (SELECT COUNT(*)
           FROM information_schema.statistics
          WHERE table_schema = DATABASE()
            AND table_name   = 'admin_operators'
            AND index_name   = 'uk_admin_operators_oidc_subject') = 0,
        'ALTER TABLE admin_operators ADD UNIQUE INDEX uk_admin_operators_oidc_subject (oidc_subject)',
        'SELECT 1'
    )
);
PREPARE stmt_add_idx FROM @ddl_add_idx;
EXECUTE stmt_add_idx;
DEALLOCATE PREPARE stmt_add_idx;
