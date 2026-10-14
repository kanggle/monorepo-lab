-- TASK-BE-633 (ADR-MONO-083 § Outstanding · ADR-MONO-046 D5) — one admin-role row per SOURCE.
--
-- Before this file `admin_operator_roles` held ONE row per (operator, role, tenant) and recorded where it came from
-- in `group_origin` / `org_node_id`, columns outside the key. The first writer owned the row:
--   * G1 and G2 both grant role R to the same member → G1's fan-out writes the row, G2's is skipped; the member
--     leaves G1 (or G1 revokes R, or G1 is deleted) → the row goes and R is gone although G2 still grants it
--     (data-model.md «단일-소유 마커(v1 규약)»);
--   * a direct grant over a group row (role PATCH) or a node grant over a direct / group row re-saves the SAME key,
--     and JPA's merge rewrites the existing row into the new origin — no audit says the source changed.
-- The fix is the TASK-BE-631 shape (operator_domain_set_grant): the source is part of the PRIMARY KEY.
--
--   source_key  'DIRECT'      a direct grant (role PATCH · invitation · onboarding · operator creation · seeds)
--               'NODE'        an org-node grant (ORG_ADMIN @ node — org_node_id set). Still at most ONE node per
--                             (operator, role, tenant): a re-grant at another node moves it (OrgNodeAdminUseCase).
--               'GROUP:<id>'  a group fan-out row of operator_group <id> (= group_origin)
--
-- Steps (each re-run safe; the file is forward-only):
--   1. ADD COLUMN source_key VARCHAR(48) NOT NULL DEFAULT 'DIRECT' — every existing row reads 'DIRECT' for a moment,
--      and every hand-written / seed INSERT that names no source_key keeps meaning «a direct grant» (R__ seeds,
--      scripts/console-demo, e2e fixtures).
--   2. Derive the source of the existing rows from the columns that already recorded it (no other column changes):
--      org_node_id set → 'NODE' (the scope driver wins — it decides what the row means to evaluation);
--      group_origin set → 'GROUP:<group_origin>'; otherwise 'DIRECT'. Runs while the key is still
--      (operator_id, role_id, tenant_id), so no two rows can collide.
--   3. Swap the PRIMARY KEY to (operator_id, role_id, tenant_id, source_key) in ONE ALTER (operator_id stays leading:
--      fk_admin_operator_roles_operator needs it — a separate DROP PRIMARY KEY fails with ER 1553, see V0051).
--   4. Two CHECKs on columns that carry no FK referential action (MySQL refuses a CHECK on group_origin — ER 3823,
--      same limit as V0052): the source vocabulary with «a GROUP row is never in '*'», and «NODE ⟺ org_node_id».
--      The «GROUP:<id> = group_origin» half is application-held and pinned by V0053MigrationShapeTest +
--      AdminRoleSourceIntegrationTest. Existing data cannot violate either CHECK: step 2 derives the source from
--      exactly those columns, and V0042's ck_admin_operator_roles_node_not_platform already keeps nodes out of '*'.
--   5. 🔴 RE-FAN-OUT (TASK-BE-633 Failure Scenario 1). Changing the key alone would leave every source that the v1
--      rule SKIPPED unrecorded forever: G2's share of a role G1 materialised first, a group's share of a role the
--      member also held directly. For every ROLE template × current member, the GROUP:<id> row is inserted where it
--      is missing — in the group's tenant (784 D-11), only where the member still belongs to that tenant (784 D-1:
--      no role row without a membership), never for a '*' operator (ADR-082 D2 — cannot be a member anyway).
--      granted_by = the template's grantor; granted_at = now (the moment the row is materialised).
--      🔵 This can GIVE a member a role it does not hold today: under v1, when G1 was revoked while G2 still granted
--      the role, the row went and was never re-materialised. That is the defect being repaired, not a new grant —
--      G2's template says the member holds it. data-model.md § Migration Strategy V0053 has the read-only query
--      that lists those rows before deployment.
--
-- NET-ZERO FOR EVALUATION. PermissionEvaluator / AdminGrantScopeEvaluator / the role lists union or de-duplicate
-- rows; two rows of the same role are the same permissions and the same tenant scope as one. Nothing in the
-- admin JWT carries roles.
--
-- Shape contract (V0016 cycle-3 lesson, same as V0042 / V0044 / V0051): forward-only, no rollback; each DDL guard is
-- one self-contained SET / PREPARE / EXECUTE / DEALLOCATE quartet derived from INFORMATION_SCHEMA (no user variable
-- read by a later statement); NULL-safe guards (`(SELECT COUNT(*) …) = 0`). MySQL-only.

-- ========================================================================
-- 1. source_key column
-- ========================================================================
SET @ddl_aor_src_col := (
    SELECT IF(
        (SELECT COUNT(*)
           FROM information_schema.columns
          WHERE table_schema = DATABASE()
            AND table_name   = 'admin_operator_roles'
            AND column_name  = 'source_key') = 0,
        'ALTER TABLE admin_operator_roles ADD COLUMN source_key VARCHAR(48) NOT NULL DEFAULT ''DIRECT'' AFTER group_origin',
        'SELECT 1'
    )
);
PREPARE stmt_aor_src_col FROM @ddl_aor_src_col;
EXECUTE stmt_aor_src_col;
DEALLOCATE PREPARE stmt_aor_src_col;

-- ========================================================================
-- 2. Source of the existing rows (only rows still reading the column default and carrying an origin)
-- ========================================================================
UPDATE admin_operator_roles
   SET source_key = CASE
                        WHEN org_node_id IS NOT NULL THEN 'NODE'
                        ELSE CONCAT('GROUP:', group_origin)
                    END
 WHERE source_key = 'DIRECT'
   AND (org_node_id IS NOT NULL OR group_origin IS NOT NULL);

-- ========================================================================
-- 3. PRIMARY KEY (operator_id, role_id, tenant_id) → (operator_id, role_id, tenant_id, source_key)
-- ========================================================================
SET @ddl_aor_src_pk := (
    SELECT IF(
        (SELECT COUNT(*)
           FROM information_schema.key_column_usage
          WHERE table_schema    = DATABASE()
            AND table_name      = 'admin_operator_roles'
            AND constraint_name = 'PRIMARY'
            AND column_name     = 'source_key') = 0,
        'ALTER TABLE admin_operator_roles DROP PRIMARY KEY, ADD PRIMARY KEY (operator_id, role_id, tenant_id, source_key)',
        'SELECT 1'
    )
);
PREPARE stmt_aor_src_pk FROM @ddl_aor_src_pk;
EXECUTE stmt_aor_src_pk;
DEALLOCATE PREPARE stmt_aor_src_pk;

-- ========================================================================
-- 4. CHECKs (columns without an FK referential action only — ER 3823)
-- ========================================================================
SET @ddl_aor_src_chk := (
    SELECT IF(
        (SELECT COUNT(*)
           FROM information_schema.table_constraints
          WHERE constraint_schema = DATABASE()
            AND table_name        = 'admin_operator_roles'
            AND constraint_name   = 'ck_admin_operator_roles_source') = 0,
        'ALTER TABLE admin_operator_roles ADD CONSTRAINT ck_admin_operator_roles_source CHECK (source_key IN (''DIRECT'', ''NODE'') OR (source_key LIKE ''GROUP:%'' AND tenant_id <> ''*''))',
        'SELECT 1'
    )
);
PREPARE stmt_aor_src_chk FROM @ddl_aor_src_chk;
EXECUTE stmt_aor_src_chk;
DEALLOCATE PREPARE stmt_aor_src_chk;

SET @ddl_aor_node_chk := (
    SELECT IF(
        (SELECT COUNT(*)
           FROM information_schema.table_constraints
          WHERE constraint_schema = DATABASE()
            AND table_name        = 'admin_operator_roles'
            AND constraint_name   = 'ck_admin_operator_roles_node_source') = 0,
        'ALTER TABLE admin_operator_roles ADD CONSTRAINT ck_admin_operator_roles_node_source CHECK ((source_key = ''NODE'') = (org_node_id IS NOT NULL))',
        'SELECT 1'
    )
);
PREPARE stmt_aor_node_chk FROM @ddl_aor_node_chk;
EXECUTE stmt_aor_node_chk;
DEALLOCATE PREPARE stmt_aor_node_chk;

-- ========================================================================
-- 5. Re-fan-out: every ROLE template × current member → its GROUP:<id> row, where missing
-- ========================================================================
INSERT INTO admin_operator_roles
    (operator_id, role_id, tenant_id, granted_at, granted_by, org_node_id, group_origin, source_key)
SELECT m.operator_id, gg.role_id, g.tenant_id, NOW(6), gg.granted_by, NULL, g.id, CONCAT('GROUP:', g.id)
  FROM operator_group_grant gg
  JOIN operator_group g        ON g.id = gg.group_id
  JOIN operator_group_member m ON m.group_id = g.id
  JOIN admin_operators o       ON o.id = m.operator_id
 WHERE gg.grant_type = 'ROLE'
   AND gg.role_id IS NOT NULL
   AND o.tenant_id <> '*'
   AND g.tenant_id <> '*'
   AND EXISTS (
        SELECT 1
          FROM operator_tenant_assignment a
         WHERE a.operator_id = m.operator_id
           AND a.tenant_id   = g.tenant_id
       )
   AND NOT EXISTS (
        SELECT 1
          FROM (SELECT operator_id, role_id, tenant_id, source_key FROM admin_operator_roles) x
         WHERE x.operator_id = m.operator_id
           AND x.role_id     = gg.role_id
           AND x.tenant_id   = g.tenant_id
           AND x.source_key  = CONCAT('GROUP:', g.id)
       );
