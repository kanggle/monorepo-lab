-- TASK-MONO-793 (ADR-MONO-083 P6 · owner decisions, 2026-10-15 UTC) - ERP fine-grained domain roles.
-- Canonical: admin-api.md § 카탈로그 · § 시스템 프리셋; data-model.md § Migration Strategy (TASK-MONO-793);
-- erp iam-integration.md § 업무 역할 (what each role opens).
--
-- The catalog (DomainRoleCatalog) replaced ('erp', 'ERP_OPERATOR') with four ERP business roles. ERP_OPERATOR is now
-- the ENTRY role: auth-service derives it when >= 1 ERP business role rides the token; it is never assigned and opens
-- no business API on its own. This file moves the DATA to match, so every existing operator keeps the same scope.
-- Same shape as V0056 (SCM, P5):
--   1. three ERP presets with FIXED set ids (INSERT IGNORE - same ids in every environment);
--   2. every set holding ('erp', 'ERP_OPERATOR') - presets erp-full / all-domains-full / legacy-compat AND every
--      tenant set (a tenant set that held it was made as «ERP 전체 업무») - gains the four roles FIRST,
--      then the ERP_OPERATOR rows are deleted. The order is the safety: no set is ever «ERP 0», not even mid-file;
--   3. the erp-full description stops saying «세분 미지원»;
--   4. partnership scopes (tenant_partnership.delegated_scope, tenant_partnership_participant.participant_scope -
--      JSON {"domains": [...], "roles": [...]}) get the same rewrite, guarded by JSON_CONTAINS (rows without the role
--      are not touched). auth carries delegated roles verbatim, so an unrewritten delegation would lose ERP at deploy.
--
-- Re-run = 0 rows (INSERT IGNORE on PKs · DELETE finds nothing · both UPDATEs are guarded). Forward-only. MySQL-only.
-- No cross-statement user variable (V0016 cycle-2 trap). No semicolon inside any literal (the ITs split on it).
-- Shape: V0057MigrationShapeTest · behaviour on real MySQL: V0057ErpRoleMigrationIntegrationTest.

-- ========================================================================
-- 1. Presets (admin-api.md § 시스템 프리셋)
-- ========================================================================
INSERT IGNORE INTO domain_permission_set
    (set_id, tenant_id, system_key, name, description, created_by, created_at, updated_at, version)
VALUES
    ('00000083-0000-7000-8000-000000000021', NULL, 'erp-read',               'ERP 조회',      'ERP 기준정보 · 결재 조회',   NULL, NOW(6), NOW(6), 0),
    ('00000083-0000-7000-8000-000000000022', NULL, 'erp-masterdata-operate', '기준정보 운영', 'ERP 기준정보 조회 · 변경', NULL, NOW(6), NOW(6), 0),
    ('00000083-0000-7000-8000-000000000023', NULL, 'erp-approval-operate',   '결재 운영',     'ERP 결재 조회 · 변경',     NULL, NOW(6), NOW(6), 0);

INSERT IGNORE INTO domain_permission_set_role (set_id, domain_key, role_name)
SELECT s.id, v.domain_key, v.role_name
  FROM domain_permission_set s
  JOIN (
        SELECT 'erp-read' AS system_key, 'erp' AS domain_key, 'ERP_MASTERDATA_READ' AS role_name
        UNION ALL SELECT 'erp-read',               'erp', 'ERP_APPROVAL_READ'
        UNION ALL SELECT 'erp-masterdata-operate', 'erp', 'ERP_MASTERDATA_READ'
        UNION ALL SELECT 'erp-masterdata-operate', 'erp', 'ERP_MASTERDATA_WRITE'
        UNION ALL SELECT 'erp-approval-operate',   'erp', 'ERP_APPROVAL_READ'
        UNION ALL SELECT 'erp-approval-operate',   'erp', 'ERP_APPROVAL_WRITE'
       ) v ON v.system_key = s.system_key;

-- ========================================================================
-- 2. Every set holding ERP_OPERATOR: add the four roles, THEN delete ERP_OPERATOR
-- ========================================================================
INSERT IGNORE INTO domain_permission_set_role (set_id, domain_key, role_name)
SELECT h.set_id, 'erp', v.role_name
  FROM (SELECT DISTINCT set_id FROM domain_permission_set_role
         WHERE domain_key = 'erp' AND role_name = 'ERP_OPERATOR') h
  CROSS JOIN (
        SELECT 'ERP_MASTERDATA_READ' AS role_name
        UNION ALL SELECT 'ERP_MASTERDATA_WRITE'
        UNION ALL SELECT 'ERP_APPROVAL_READ'
        UNION ALL SELECT 'ERP_APPROVAL_WRITE'
       ) v;

DELETE FROM domain_permission_set_role WHERE domain_key = 'erp' AND role_name = 'ERP_OPERATOR';

-- ========================================================================
-- 3. erp-full description (guarded - a re-run matches 0 rows)
-- ========================================================================
UPDATE domain_permission_set
   SET description = 'ERP 카탈로그 역할 전부(기준정보 · 결재)', updated_at = NOW(6)
 WHERE system_key = 'erp-full'
   AND description <> 'ERP 카탈로그 역할 전부(기준정보 · 결재)';

-- ========================================================================
-- 4. Partnership scopes: ERP_OPERATOR -> the four roles (JSON_CONTAINS-guarded)
-- ========================================================================
UPDATE tenant_partnership
   SET delegated_scope = JSON_SET(delegated_scope, '$.roles',
           JSON_MERGE_PRESERVE(
               JSON_REMOVE(JSON_EXTRACT(delegated_scope, '$.roles'),
                           JSON_UNQUOTE(JSON_SEARCH(JSON_EXTRACT(delegated_scope, '$.roles'), 'one', 'ERP_OPERATOR'))),
               JSON_ARRAY('ERP_MASTERDATA_READ', 'ERP_MASTERDATA_WRITE',
                          'ERP_APPROVAL_READ', 'ERP_APPROVAL_WRITE'))),
       updated_at = NOW(6)
 WHERE JSON_CONTAINS(JSON_EXTRACT(delegated_scope, '$.roles'), '"ERP_OPERATOR"');

UPDATE tenant_partnership_participant
   SET participant_scope = JSON_SET(participant_scope, '$.roles',
           JSON_MERGE_PRESERVE(
               JSON_REMOVE(JSON_EXTRACT(participant_scope, '$.roles'),
                           JSON_UNQUOTE(JSON_SEARCH(JSON_EXTRACT(participant_scope, '$.roles'), 'one', 'ERP_OPERATOR'))),
               JSON_ARRAY('ERP_MASTERDATA_READ', 'ERP_MASTERDATA_WRITE',
                          'ERP_APPROVAL_READ', 'ERP_APPROVAL_WRITE')))
 WHERE participant_scope IS NOT NULL
   AND JSON_CONTAINS(JSON_EXTRACT(participant_scope, '$.roles'), '"ERP_OPERATOR"');
