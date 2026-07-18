-- TASK-BE-520 / ADR-MONO-046 D5 (§ 4 step 2) — the `group_origin` fan-out marker on the
-- shared assignment substrate (`admin_operator_roles` + `operator_tenant_assignment`).
--
-- ------------------------------------------------------------------------
-- What this column is, and what it is NOT
-- ------------------------------------------------------------------------
-- `group_origin` NOT NULL ⇒ this flat grant row was MATERIALISED by a fan-out of the named
--                           operator_group's grant (D5 cascade trail — sibling of ADR-045).
-- `group_origin` NULL     ⇒ a DIRECT grant. Legacy behaviour, byte-unchanged.
--
-- The marker is lifecycle bookkeeping ONLY. Evaluation never reads it: a fan-out row is
-- indistinguishable from a direct grant to `PermissionEvaluator` / the perm-cache
-- (rbac.md § Operator Group Fan-Out) — evaluation / cache / all confinement axes stay
-- byte-unchanged (ADR-046 D2-A, v1 fan-out; NOT inheritance).
--
-- cascade-revoke (remove-member / delete-group / revoke-grant) filters STRICTLY on
-- `group_origin = <groupId>`, so a direct grant (`group_origin IS NULL`) is never destroyed
-- (data-model.md § group_origin idempotence invariant). A REAL FK to operator_group.id (same
-- physical database — unlike the opaque org_node_id cross-service ref) with ON DELETE CASCADE
-- gives a DB-level safety net on delete-group; the application ALSO revokes explicitly for
-- audit / outbox atomicity (D5/D6).
--
-- ------------------------------------------------------------------------
-- Shape contract (V0016 cycle-3 lesson, applied verbatim — same as V0042)
-- ------------------------------------------------------------------------
--   1. FORWARD-ONLY. No down/rollback statement.
--   2. NO cross-statement MySQL user variable: every idempotency guard, dynamic-SQL string
--      and EXECUTE is a self-contained PREPARE/EXECUTE/DEALLOCATE triple derived purely from
--      INFORMATION_SCHEMA at execution time.
--   3. NULL-SAFE guard: `(SELECT COUNT(*) ...) = 0` — COUNT(*) is always an integer, never
--      NULL, so the predicate is never NULL. Safely re-runnable.
--
-- NET-ZERO: two additive NULLABLE columns (DEFAULT NULL) — every existing direct grant row
-- stays byte-identical with `group_origin IS NULL`. That the marker is nullable+defaulted IS
-- why the migration is backward-compatible. MySQL-only.

-- ========================================================================
-- admin_operator_roles.group_origin  (ROLE fan-out)
-- ========================================================================
SET @ddl_aor_col := (
    SELECT IF(
        (SELECT COUNT(*)
           FROM information_schema.columns
          WHERE table_schema = DATABASE()
            AND table_name   = 'admin_operator_roles'
            AND column_name  = 'group_origin') = 0,
        'ALTER TABLE admin_operator_roles ADD COLUMN group_origin BIGINT NULL DEFAULT NULL AFTER org_node_id',
        'SELECT 1'
    )
);
PREPARE stmt_aor_col FROM @ddl_aor_col;
EXECUTE stmt_aor_col;
DEALLOCATE PREPARE stmt_aor_col;

SET @ddl_aor_idx := (
    SELECT IF(
        (SELECT COUNT(*)
           FROM information_schema.statistics
          WHERE table_schema = DATABASE()
            AND table_name   = 'admin_operator_roles'
            AND index_name   = 'idx_admin_operator_roles_group_origin') = 0,
        'ALTER TABLE admin_operator_roles ADD INDEX idx_admin_operator_roles_group_origin (group_origin)',
        'SELECT 1'
    )
);
PREPARE stmt_aor_idx FROM @ddl_aor_idx;
EXECUTE stmt_aor_idx;
DEALLOCATE PREPARE stmt_aor_idx;

SET @ddl_aor_fk := (
    SELECT IF(
        (SELECT COUNT(*)
           FROM information_schema.table_constraints
          WHERE constraint_schema = DATABASE()
            AND table_name        = 'admin_operator_roles'
            AND constraint_name   = 'fk_admin_operator_roles_group_origin') = 0,
        'ALTER TABLE admin_operator_roles ADD CONSTRAINT fk_admin_operator_roles_group_origin FOREIGN KEY (group_origin) REFERENCES operator_group(id) ON DELETE CASCADE',
        'SELECT 1'
    )
);
PREPARE stmt_aor_fk FROM @ddl_aor_fk;
EXECUTE stmt_aor_fk;
DEALLOCATE PREPARE stmt_aor_fk;

-- ========================================================================
-- operator_tenant_assignment.group_origin  (TENANT_ASSIGNMENT fan-out)
-- ========================================================================
SET @ddl_ota_col := (
    SELECT IF(
        (SELECT COUNT(*)
           FROM information_schema.columns
          WHERE table_schema = DATABASE()
            AND table_name   = 'operator_tenant_assignment'
            AND column_name  = 'group_origin') = 0,
        'ALTER TABLE operator_tenant_assignment ADD COLUMN group_origin BIGINT NULL DEFAULT NULL',
        'SELECT 1'
    )
);
PREPARE stmt_ota_col FROM @ddl_ota_col;
EXECUTE stmt_ota_col;
DEALLOCATE PREPARE stmt_ota_col;

SET @ddl_ota_idx := (
    SELECT IF(
        (SELECT COUNT(*)
           FROM information_schema.statistics
          WHERE table_schema = DATABASE()
            AND table_name   = 'operator_tenant_assignment'
            AND index_name   = 'idx_operator_tenant_assignment_group_origin') = 0,
        'ALTER TABLE operator_tenant_assignment ADD INDEX idx_operator_tenant_assignment_group_origin (group_origin)',
        'SELECT 1'
    )
);
PREPARE stmt_ota_idx FROM @ddl_ota_idx;
EXECUTE stmt_ota_idx;
DEALLOCATE PREPARE stmt_ota_idx;

SET @ddl_ota_fk := (
    SELECT IF(
        (SELECT COUNT(*)
           FROM information_schema.table_constraints
          WHERE constraint_schema = DATABASE()
            AND table_name        = 'operator_tenant_assignment'
            AND constraint_name   = 'fk_operator_tenant_assignment_group_origin') = 0,
        'ALTER TABLE operator_tenant_assignment ADD CONSTRAINT fk_operator_tenant_assignment_group_origin FOREIGN KEY (group_origin) REFERENCES operator_group(id) ON DELETE CASCADE',
        'SELECT 1'
    )
);
PREPARE stmt_ota_fk FROM @ddl_ota_fk;
EXECUTE stmt_ota_fk;
DEALLOCATE PREPARE stmt_ota_fk;
