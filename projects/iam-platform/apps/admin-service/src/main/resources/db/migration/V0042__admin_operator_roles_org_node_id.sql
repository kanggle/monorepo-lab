-- TASK-BE-492 / ADR-MONO-047 D5 — `admin_operator_roles.org_node_id`: the SCOPE DRIVER
-- for an org-node-scoped grant (ORG_ADMIN @ node).
--
-- ------------------------------------------------------------------------
-- What this column is, and what it is NOT
-- ------------------------------------------------------------------------
-- `org_node_id` NOT NULL  ⇒ this grant's effective admin scope is the set of tenants in
--                           the node's SUBTREE (resolved at request time from
--                           account-service `GET /internal/org-nodes/{id}/tenants`).
-- `org_node_id` NULL      ⇒ legacy tenant-scoped grant. Byte-unchanged behaviour.
--
-- `tenant_id` is NOT repurposed. It keeps mirroring the bound operator's OWN
-- `admin_operators.tenant_id` (the TASK-BE-289 WI-2 per-tenant binding invariant) — it is
-- the audit-routing / row-isolation column. For a TENANT_ADMIN it *coincides* with the
-- scope; for an ORG_ADMIN it does not. Repurposing it would break audit attribution AND
-- would silently promote a company admin to platform admin via the `'*'` short-circuit.
--
-- CHECK: a platform-scoped grant (`tenant_id='*'`, i.e. SUPER_ADMIN) may not also carry a
-- node — platform already reaches everything, so the combination is meaningless, and the
-- `'*'` pre-scan in `effectiveAdminScope` would make the node silently inert. Forbidden at
-- the DB so a hand-written seed / ops SQL row cannot create an ambiguous grant, and at the
-- application layer so the API rejects it with a message.
--
-- NO FOREIGN KEY: `org_node` lives in account_db (account-service owns `tenants`, therefore
-- `org_node` — ADR-047 D6). admin_db is a physically separate database, so this is an
-- OPAQUE cross-service reference by value convention — the same shape as
-- `admin_operators.oidc_subject` (V0027) and `finance_default_account_id` (V0029).
--
-- ------------------------------------------------------------------------
-- Shape contract (V0016 cycle-3 lesson, applied verbatim — same as V0027/V0029)
-- ------------------------------------------------------------------------
--   1. FORWARD-ONLY. No down/rollback statement.
--   2. NO cross-statement MySQL user variable: every idempotency guard, dynamic-SQL
--      string and EXECUTE is a self-contained PREPARE/EXECUTE/DEALLOCATE triple whose
--      branch is derived purely from INFORMATION_SCHEMA at execution time.
--   3. NULL-SAFE guard: `(SELECT COUNT(*) ...) = 0` — COUNT(*) is always an integer,
--      never NULL, so the predicate is never NULL. Safely re-runnable.
--
-- NET-ZERO: additive nullable column + non-unique index + a CHECK that no existing row can
-- violate (every existing row has org_node_id IS NULL, so the CHECK is vacuously true).
-- MySQL-only (information_schema, PREPARE/EXECUTE) — admin-service runs only MySQL 8.0.

-- ------------------------------------------------------------------------
-- 1. Add the org_node_id column (idempotent, self-contained).
-- ------------------------------------------------------------------------
SET @ddl_add_col := (
    SELECT IF(
        (SELECT COUNT(*)
           FROM information_schema.columns
          WHERE table_schema = DATABASE()
            AND table_name   = 'admin_operator_roles'
            AND column_name  = 'org_node_id') = 0,
        'ALTER TABLE admin_operator_roles ADD COLUMN org_node_id VARCHAR(36) NULL AFTER tenant_id',
        'SELECT 1'
    )
);
PREPARE stmt_add_col FROM @ddl_add_col;
EXECUTE stmt_add_col;
DEALLOCATE PREPARE stmt_add_col;

-- ------------------------------------------------------------------------
-- 2. Index the scope driver — `GET /api/admin/org-nodes/{id}/admins` and the
--    grant/revoke surface look grants up by node.
-- ------------------------------------------------------------------------
SET @ddl_add_idx := (
    SELECT IF(
        (SELECT COUNT(*)
           FROM information_schema.statistics
          WHERE table_schema = DATABASE()
            AND table_name   = 'admin_operator_roles'
            AND index_name   = 'idx_admin_operator_roles_org_node') = 0,
        'ALTER TABLE admin_operator_roles ADD INDEX idx_admin_operator_roles_org_node (org_node_id)',
        'SELECT 1'
    )
);
PREPARE stmt_add_idx FROM @ddl_add_idx;
EXECUTE stmt_add_idx;
DEALLOCATE PREPARE stmt_add_idx;

-- ------------------------------------------------------------------------
-- 3. Forbid `org_node_id IS NOT NULL AND tenant_id = '*'` at the DB (idempotent).
--    Enforced from MySQL 8.0.16 onwards; admin-service pins mysql:8.0.
-- ------------------------------------------------------------------------
SET @ddl_add_chk := (
    SELECT IF(
        (SELECT COUNT(*)
           FROM information_schema.table_constraints
          WHERE constraint_schema = DATABASE()
            AND table_name        = 'admin_operator_roles'
            AND constraint_name   = 'ck_admin_operator_roles_node_not_platform') = 0,
        'ALTER TABLE admin_operator_roles ADD CONSTRAINT ck_admin_operator_roles_node_not_platform CHECK (org_node_id IS NULL OR tenant_id <> ''*'')',
        'SELECT 1'
    )
);
PREPARE stmt_add_chk FROM @ddl_add_chk;
EXECUTE stmt_add_chk;
DEALLOCATE PREPARE stmt_add_chk;
