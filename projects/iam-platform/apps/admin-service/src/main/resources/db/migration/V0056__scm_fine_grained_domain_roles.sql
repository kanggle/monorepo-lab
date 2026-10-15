-- TASK-MONO-792 (ADR-MONO-083 P5 · owner decisions Q1-Q5, 2026-10-15 UTC) - SCM fine-grained domain roles.
-- Canonical: admin-api.md § 카탈로그 · § 시스템 프리셋; data-model.md § Migration Strategy (TASK-MONO-792);
-- scm iam-integration.md § 업무 역할 (what each role opens).
--
-- The catalog (DomainRoleCatalog) replaced ('scm', 'SCM_OPERATOR') with eight SCM business roles. SCM_OPERATOR is now
-- the ENTRY role: auth-service derives it when >= 1 SCM business role rides the token; it is never assigned and opens
-- no business API on its own. This file moves the DATA to match, so every existing operator keeps the same scope:
--   1. five read-only SCM presets with FIXED set ids (INSERT IGNORE - same ids in every environment);
--   2. every set holding ('scm', 'SCM_OPERATOR') - presets scm-full / all-domains-full / legacy-compat AND every
--      tenant set (a tenant set that held it was made as «SCM 전체 업무») - gains the eight roles FIRST,
--      then the SCM_OPERATOR rows are deleted. The order is the safety: no set is ever «SCM 0», not even mid-file;
--   3. the scm-full description stops saying «세분 미지원»;
--   4. partnership scopes (tenant_partnership.delegated_scope, tenant_partnership_participant.participant_scope -
--      JSON {"domains": [...], "roles": [...]}) get the same rewrite, guarded by JSON_CONTAINS (rows without the role
--      are not touched). auth carries delegated roles verbatim, so an unrewritten delegation would lose SCM at deploy.
--
-- Re-run = 0 rows (INSERT IGNORE on PKs · DELETE finds nothing · both UPDATEs are guarded). Forward-only. MySQL-only.
-- No cross-statement user variable (V0016 cycle-2 trap). No ';' inside any literal (the ITs split on ';').
-- Shape: V0056MigrationShapeTest · behaviour on real MySQL: V0056ScmRoleMigrationIntegrationTest.

-- ========================================================================
-- 1. Presets (admin-api.md § 시스템 프리셋)
-- ========================================================================
INSERT IGNORE INTO domain_permission_set
    (set_id, tenant_id, system_key, name, description, created_by, created_at, updated_at, version)
VALUES
    ('00000083-0000-7000-8000-000000000016', NULL, 'scm-read',                         'SCM 조회',         'SCM 발주 · 수요계획 · 물류 · 재고 가시성 조회', NULL, NOW(6), NOW(6), 0),
    ('00000083-0000-7000-8000-000000000017', NULL, 'scm-procurement-operate',          '발주 운영',        'SCM 발주 조회 · 변경',                        NULL, NOW(6), NOW(6), 0),
    ('00000083-0000-7000-8000-000000000018', NULL, 'scm-demand-planning-operate',      '수요계획 운영',    'SCM 수요계획 조회 · 변경',                    NULL, NOW(6), NOW(6), 0),
    ('00000083-0000-7000-8000-000000000019', NULL, 'scm-logistics-operate',            '물류 운영',        'SCM 물류(배차) 조회 · 발송 재시도',           NULL, NOW(6), NOW(6), 0),
    ('00000083-0000-7000-8000-000000000020', NULL, 'scm-inventory-visibility-operate', '재고 가시성 운영', 'SCM 재고 가시성 조회 · 3PL 온보딩',          NULL, NOW(6), NOW(6), 0);

INSERT IGNORE INTO domain_permission_set_role (set_id, domain_key, role_name)
SELECT s.id, v.domain_key, v.role_name
  FROM domain_permission_set s
  JOIN (
        SELECT 'scm-read' AS system_key, 'scm' AS domain_key, 'SCM_PROCUREMENT_READ' AS role_name
        UNION ALL SELECT 'scm-read',                         'scm', 'SCM_DEMAND_PLANNING_READ'
        UNION ALL SELECT 'scm-read',                         'scm', 'SCM_LOGISTICS_READ'
        UNION ALL SELECT 'scm-read',                         'scm', 'SCM_INVENTORY_VISIBILITY_READ'
        UNION ALL SELECT 'scm-procurement-operate',          'scm', 'SCM_PROCUREMENT_READ'
        UNION ALL SELECT 'scm-procurement-operate',          'scm', 'SCM_PROCUREMENT_WRITE'
        UNION ALL SELECT 'scm-demand-planning-operate',      'scm', 'SCM_DEMAND_PLANNING_READ'
        UNION ALL SELECT 'scm-demand-planning-operate',      'scm', 'SCM_DEMAND_PLANNING_WRITE'
        UNION ALL SELECT 'scm-logistics-operate',            'scm', 'SCM_LOGISTICS_READ'
        UNION ALL SELECT 'scm-logistics-operate',            'scm', 'SCM_LOGISTICS_WRITE'
        UNION ALL SELECT 'scm-inventory-visibility-operate', 'scm', 'SCM_INVENTORY_VISIBILITY_READ'
        UNION ALL SELECT 'scm-inventory-visibility-operate', 'scm', 'SCM_INVENTORY_VISIBILITY_WRITE'
       ) v ON v.system_key = s.system_key;

-- ========================================================================
-- 2. Every set holding SCM_OPERATOR: add the eight roles, THEN delete SCM_OPERATOR
-- ========================================================================
INSERT IGNORE INTO domain_permission_set_role (set_id, domain_key, role_name)
SELECT h.set_id, 'scm', v.role_name
  FROM (SELECT DISTINCT set_id FROM domain_permission_set_role
         WHERE domain_key = 'scm' AND role_name = 'SCM_OPERATOR') h
  CROSS JOIN (
        SELECT 'SCM_PROCUREMENT_READ' AS role_name
        UNION ALL SELECT 'SCM_PROCUREMENT_WRITE'
        UNION ALL SELECT 'SCM_DEMAND_PLANNING_READ'
        UNION ALL SELECT 'SCM_DEMAND_PLANNING_WRITE'
        UNION ALL SELECT 'SCM_LOGISTICS_READ'
        UNION ALL SELECT 'SCM_LOGISTICS_WRITE'
        UNION ALL SELECT 'SCM_INVENTORY_VISIBILITY_READ'
        UNION ALL SELECT 'SCM_INVENTORY_VISIBILITY_WRITE'
       ) v;

DELETE FROM domain_permission_set_role WHERE domain_key = 'scm' AND role_name = 'SCM_OPERATOR';

-- ========================================================================
-- 3. scm-full description (guarded - a re-run matches 0 rows)
-- ========================================================================
UPDATE domain_permission_set
   SET description = 'SCM 카탈로그 역할 전부(발주 · 수요계획 · 물류 · 재고 가시성)', updated_at = NOW(6)
 WHERE system_key = 'scm-full'
   AND description <> 'SCM 카탈로그 역할 전부(발주 · 수요계획 · 물류 · 재고 가시성)';

-- ========================================================================
-- 4. Partnership scopes: SCM_OPERATOR -> the eight roles (JSON_CONTAINS-guarded)
-- ========================================================================
UPDATE tenant_partnership
   SET delegated_scope = JSON_SET(delegated_scope, '$.roles',
           JSON_MERGE_PRESERVE(
               JSON_REMOVE(JSON_EXTRACT(delegated_scope, '$.roles'),
                           JSON_UNQUOTE(JSON_SEARCH(JSON_EXTRACT(delegated_scope, '$.roles'), 'one', 'SCM_OPERATOR'))),
               JSON_ARRAY('SCM_PROCUREMENT_READ', 'SCM_PROCUREMENT_WRITE',
                          'SCM_DEMAND_PLANNING_READ', 'SCM_DEMAND_PLANNING_WRITE',
                          'SCM_LOGISTICS_READ', 'SCM_LOGISTICS_WRITE',
                          'SCM_INVENTORY_VISIBILITY_READ', 'SCM_INVENTORY_VISIBILITY_WRITE'))),
       updated_at = NOW(6)
 WHERE JSON_CONTAINS(JSON_EXTRACT(delegated_scope, '$.roles'), '"SCM_OPERATOR"');

UPDATE tenant_partnership_participant
   SET participant_scope = JSON_SET(participant_scope, '$.roles',
           JSON_MERGE_PRESERVE(
               JSON_REMOVE(JSON_EXTRACT(participant_scope, '$.roles'),
                           JSON_UNQUOTE(JSON_SEARCH(JSON_EXTRACT(participant_scope, '$.roles'), 'one', 'SCM_OPERATOR'))),
               JSON_ARRAY('SCM_PROCUREMENT_READ', 'SCM_PROCUREMENT_WRITE',
                          'SCM_DEMAND_PLANNING_READ', 'SCM_DEMAND_PLANNING_WRITE',
                          'SCM_LOGISTICS_READ', 'SCM_LOGISTICS_WRITE',
                          'SCM_INVENTORY_VISIBILITY_READ', 'SCM_INVENTORY_VISIBILITY_WRITE')))
 WHERE participant_scope IS NOT NULL
   AND JSON_CONTAINS(JSON_EXTRACT(participant_scope, '$.roles'), '"SCM_OPERATOR"');
