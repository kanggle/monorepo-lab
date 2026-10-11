-- TASK-MONO-784 S2 / ADR-MONO-082 b (decisions D-2 · D-3) — one operator row, a membership and
-- role set per company.
--
-- ------------------------------------------------------------------------
-- 1. admin_operator_roles PRIMARY KEY (operator_id, role_id) -> (operator_id, role_id, tenant_id)
-- ------------------------------------------------------------------------
-- The same role may now be held in two companies (`TENANT_ADMIN` @ A and `TENANT_ADMIN` @ B on
-- ONE operator row). data-model.md § admin_operator_roles, ADR-082 branch-comparison schema row.
--
-- DATA-SAFE: before 784 every row carried tenant_id = the operator's home (V0026, WI-2), so
-- (operator_id, role_id) unique  =>  (operator_id, role_id, tenant_id) unique. No row moves.
--
-- FK-SAFE: fk_admin_operator_roles_operator (operator_id) is served by the PRIMARY KEY's leading
-- column. Dropping and re-adding the key in ONE ALTER keeps a leading-`operator_id` index present
-- for the whole statement — a separate `DROP PRIMARY KEY` would fail with "needed in a foreign key
-- constraint" (ER 1553). fk_admin_operator_roles_role keeps its own idx_admin_operator_roles_role;
-- group_origin keeps idx_admin_operator_roles_group_origin (V0044). tenant_id is NOT NULL since V0025.
--
-- IDEMPOTENT: the swap runs only while `tenant_id` is not yet part of PRIMARY.
--
-- ------------------------------------------------------------------------
-- 2. Membership backfill — operator_tenant_assignment
-- ------------------------------------------------------------------------
-- From S2 an operator's tenant scope (assume · console registry · console eligibility · token
-- exchange · read gate) is the ASSIGNMENT rows only; the home tenant (admin_operators.tenant_id)
-- is no longer read by the decision (rider R1, D-2 — otherwise an operator removed from the
-- company that is their home could still assume into it: 784 AC-0 F4). For that to change no
-- existing operator's answer, every non-'*' operator gets a whole-tenant assignment row for
--   (a) its home tenant, and
--   (b) every tenant its non-'*' role rows sit in
-- wherever that row is missing. `org_scope`, `permission_set_id` (rider R4) and `group_origin`
-- stay NULL — a whole-tenant direct membership, exactly what the home used to mean.
-- A '*' operator gets NOTHING: it has no memberships (ADR-082 D2) and the resolver short-circuits it.
--
-- IDEMPOTENT: the NOT EXISTS guard (plus the UNION's own de-duplication) makes a second run insert
-- zero rows. Pre-existing rows — including ones with an org_scope — are never touched. The guard
-- reads the target table through a derived table (same form as auth-service V0032), so the
-- statement never selects from the table it inserts into in a correlated subquery.
--
-- 🔴 R__ seeds in db/migration-dev run AFTER every V migration, so this backfill cannot see the
-- operators they create; seeds that reached a tenant through the home alone add their own
-- assignment row (R__seed_demo_cs_operator.sql · R__seed_demo_viewer_operator.sql).
--
-- ------------------------------------------------------------------------
-- Shape contract (V0016 cycle-3 lesson, same as V0042 / V0044)
-- ------------------------------------------------------------------------
--   1. FORWARD-ONLY. No down/rollback statement.
--   2. NO cross-statement MySQL user variable: the guard, its dynamic-SQL string and EXECUTE are
--      one self-contained PREPARE/EXECUTE/DEALLOCATE triple derived from INFORMATION_SCHEMA.
--   3. NULL-SAFE guard: `(SELECT COUNT(*) ...) = 0` is never NULL.
-- MySQL-only.

-- ========================================================================
-- 1. PRIMARY KEY swap
-- ========================================================================
SET @ddl_aor_pk := (
    SELECT IF(
        (SELECT COUNT(*)
           FROM information_schema.key_column_usage
          WHERE table_schema    = DATABASE()
            AND table_name      = 'admin_operator_roles'
            AND constraint_name = 'PRIMARY'
            AND column_name     = 'tenant_id') = 0,
        'ALTER TABLE admin_operator_roles DROP PRIMARY KEY, ADD PRIMARY KEY (operator_id, role_id, tenant_id)',
        'SELECT 1'
    )
);
PREPARE stmt_aor_pk FROM @ddl_aor_pk;
EXECUTE stmt_aor_pk;
DEALLOCATE PREPARE stmt_aor_pk;

-- ========================================================================
-- 2. Membership backfill (home + role-row tenants, non-'*' operators only)
-- ========================================================================
INSERT INTO operator_tenant_assignment
    (operator_id, tenant_id, granted_at, granted_by, permission_set_id, org_scope, group_origin)
SELECT src.operator_id, src.tenant_id, NOW(6), NULL, NULL, NULL, NULL
  FROM (
        SELECT o.id AS operator_id, o.tenant_id AS tenant_id
          FROM admin_operators o
         WHERE o.tenant_id <> '*'
        UNION
        SELECT aor.operator_id AS operator_id, aor.tenant_id AS tenant_id
          FROM admin_operator_roles aor
          JOIN admin_operators o ON o.id = aor.operator_id
         WHERE o.tenant_id   <> '*'
           AND aor.tenant_id <> '*'
       ) src
 WHERE NOT EXISTS (
        SELECT 1
          FROM (SELECT operator_id, tenant_id FROM operator_tenant_assignment) a
         WHERE a.operator_id = src.operator_id
           AND a.tenant_id   = src.tenant_id
       );
