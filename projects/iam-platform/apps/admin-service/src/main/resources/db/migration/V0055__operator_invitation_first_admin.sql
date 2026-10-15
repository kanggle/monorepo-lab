-- TASK-MONO-773 S4 (owner decisions OD-1 · OD-2 · OD-5) — the «first-admin invitation» mark on operator_invitation.
--
-- POST /api/admin/tenants creates the tenant PENDING («관리자 대기») together with ONE first-admin invitation; its
-- acceptance is the only PENDING → ACTIVE path (admin-api.md § Tenant Lifecycle · data-model.md § operator_invitation).
-- This file:
--   1. adds first_admin BOOLEAN NOT NULL DEFAULT FALSE — every pre-773 row is an ordinary invitation (backfill =
--      the default, no UPDATE);
--   2. adds first_admin_pending_key — a STORED generated column, non-NULL (= tenant_id) only for a PENDING first-admin
--      row — the same technique as V0050's pending_key (MySQL has no partial unique index; a UNIQUE index admits many
--      NULLs);
--   3. adds uk_operator_invitation_first_admin_pending_key on it — «at most one PENDING first-admin invitation per
--      tenant, whatever the address». The application pre-check answers 409 first; this key stops the race.
-- first_admin_pending_key is not mapped by JPA (ddl-auto=validate checks mapped columns only — like pending_key).
--
-- Shape contract (V0016 cycle-3 lesson, same as V0051 / V0053 / V0054): forward-only, no rollback; each DDL guard is
-- one self-contained SET / PREPARE / EXECUTE / DEALLOCATE quartet derived from INFORMATION_SCHEMA; NULL-safe guards.

-- ========================================================================
-- 1. first_admin
-- ========================================================================
SET @ddl_oi_first_admin := (
    SELECT IF(
        (SELECT COUNT(*)
           FROM information_schema.columns
          WHERE table_schema = DATABASE()
            AND table_name   = 'operator_invitation'
            AND column_name  = 'first_admin') = 0,
        'ALTER TABLE operator_invitation ADD COLUMN first_admin BOOLEAN NOT NULL DEFAULT FALSE AFTER cancelled_by',
        'SELECT 1'
    )
);
PREPARE stmt_oi_first_admin FROM @ddl_oi_first_admin;
EXECUTE stmt_oi_first_admin;
DEALLOCATE PREPARE stmt_oi_first_admin;

-- ========================================================================
-- 2. first_admin_pending_key (generated)
-- ========================================================================
SET @ddl_oi_fa_key := (
    SELECT IF(
        (SELECT COUNT(*)
           FROM information_schema.columns
          WHERE table_schema = DATABASE()
            AND table_name   = 'operator_invitation'
            AND column_name  = 'first_admin_pending_key') = 0,
        'ALTER TABLE operator_invitation ADD COLUMN first_admin_pending_key VARCHAR(32) GENERATED ALWAYS AS (CASE WHEN status = ''PENDING'' AND first_admin THEN tenant_id END) STORED AFTER first_admin',
        'SELECT 1'
    )
);
PREPARE stmt_oi_fa_key FROM @ddl_oi_fa_key;
EXECUTE stmt_oi_fa_key;
DEALLOCATE PREPARE stmt_oi_fa_key;

-- ========================================================================
-- 3. one PENDING first-admin invitation per tenant
-- ========================================================================
SET @ddl_oi_fa_uk := (
    SELECT IF(
        (SELECT COUNT(*)
           FROM information_schema.statistics
          WHERE table_schema = DATABASE()
            AND table_name   = 'operator_invitation'
            AND index_name   = 'uk_operator_invitation_first_admin_pending_key') = 0,
        'ALTER TABLE operator_invitation ADD UNIQUE KEY uk_operator_invitation_first_admin_pending_key (first_admin_pending_key)',
        'SELECT 1'
    )
);
PREPARE stmt_oi_fa_uk FROM @ddl_oi_fa_uk;
EXECUTE stmt_oi_fa_uk;
DEALLOCATE PREPARE stmt_oi_fa_uk;
