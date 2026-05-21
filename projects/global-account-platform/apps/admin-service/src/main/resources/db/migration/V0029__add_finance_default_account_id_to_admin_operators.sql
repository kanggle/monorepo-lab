-- TASK-BE-304 (impl of console-integration-contract.md § 2.4.9.1 option (a))
-- Add admin_operators.finance_default_account_id — operator-chosen default
-- finance-platform account UUID emitted on console-registry-api § Per-operator
-- profile attributes as the finance product item's
-- `operatorContext.defaultAccountId`. NULL by default — every existing operator
-- row stays unchanged (MVP option (b) `forbidden / MISSING_PREREQUISITE`
-- behavior preserved until an operator's row is explicitly populated).
--
-- ------------------------------------------------------------------------
-- Version note (V0028 -> V0029)
-- ------------------------------------------------------------------------
-- The TASK-BE-304 spec PR (#689, merged on main 7a531a7d) authored this as
-- V0028. However V0028 was already taken by
-- `db/migration-dev/V0028__seed_dev_operator_oidc_subject.sql` (a
-- non-prod-profile dev seed migration loaded alongside `db/migration` via
-- `spring.flyway.locations` in dev/test profiles). Flyway's
-- CompositeMigrationResolver fails fast on duplicate version numbers
-- ("Found more than one migration with version 28"). Mechanical rename to
-- V0029 (next free version) preserves the original intent — the migration
-- version is a sequencing token, not a semantic identifier. The spec note
-- in data-model.md § Migration Strategy is amended in the same PR. The
-- migration content (single ALTER TABLE … ADD COLUMN, the V0027 discipline,
-- the column shape) is byte-identical to what the spec authored.
--
-- ------------------------------------------------------------------------
-- Shape contract (TASK-BE-298 V0027 verbatim discipline reuse)
-- ------------------------------------------------------------------------
-- This is a STRUCTURAL DDL migration (ALTER TABLE ... ADD COLUMN), NOT a
-- text/JSON value rewrite, so the V0016 JSON normalization no-op trap does
-- not apply. The relevant V0016 cycle-3 + V0027 discipline that DOES apply
-- and is honored:
--
--   1. FORWARD-ONLY. No down/rollback statement (data-model.md migration
--      policy). PII/secret column policy unaffected — finance_default_account_id
--      is a non-PII opaque foreign-system identifier classified `internal`
--      (data-model.md § Data Classification Summary).
--
--   2. NO cross-statement MySQL user variable. The V0016 cycle-2 no-op was
--      caused by `SET @x := ...` referenced across Flyway-split statements
--      resolving to NULL. The single block below is wholly SELF-CONTAINED:
--      the idempotency guard, the dynamic-SQL string, and the EXECUTE are a
--      single PREPARE/EXECUTE/DEALLOCATE triple deriving its branch purely
--      from INFORMATION_SCHEMA at execution time. No `@var` is read in a
--      later statement than it was set; the only session variable used
--      (@ddl_add_col) is written and consumed inside the SAME prepared
--      block.
--
--   3. NULL-SAFE idempotency guard. The V0016 cycle-2 no-op also came from
--      a NULL-unsafe predicate. Here the guard is
--      `(SELECT COUNT(*) FROM information_schema.columns WHERE ...) = 0`
--      — COUNT(*) is ALWAYS an integer (never NULL), so `= 0` always
--      yields TRUE or FALSE, never NULL. The migration is therefore safely
--      re-runnable: if the column already exists the COUNT is > 0 and the
--      DDL string becomes a harmless `SELECT 1` no-op.
--
--   4. NO INDEX. V0027 added 2 steps (column + UNIQUE index for
--      token-exchange single-row lookup); V0028 adds 1 step only — the
--      column. Lookups are by operator row PK (`findByOperatorId`); a
--      secondary index on `finance_default_account_id` would add write
--      cost with no read benefit. (data-model.md § Migration Strategy
--      TASK-BE-304 "인덱스 부재".)
--
-- MySQL-only (`information_schema`, PREPARE/EXECUTE) is deliberate and
-- correct: admin-service runs ONLY MySQL 8.0 (architecture.md "퍼시스턴스:
-- MySQL"; every Flyway-enabled test uses MySQLContainer("mysql:8.0")).

-- ------------------------------------------------------------------------
-- 1. Add the finance_default_account_id column (idempotent, self-contained).
--    Placed AFTER oidc_subject to keep the operator-profile columns
--    grouped (the JPA entity field ordering mirrors this).
-- ------------------------------------------------------------------------
SET @ddl_add_col := (
    SELECT IF(
        (SELECT COUNT(*)
           FROM information_schema.columns
          WHERE table_schema = DATABASE()
            AND table_name   = 'admin_operators'
            AND column_name  = 'finance_default_account_id') = 0,
        'ALTER TABLE admin_operators ADD COLUMN finance_default_account_id VARCHAR(36) NULL AFTER oidc_subject',
        'SELECT 1'
    )
);
PREPARE stmt_add_col FROM @ddl_add_col;
EXECUTE stmt_add_col;
DEALLOCATE PREPARE stmt_add_col;
