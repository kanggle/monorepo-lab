-- TASK-MONO-794 (ADR-MONO-083 P7 · owner decisions, 2026-10-16 UTC) - Finance fine-grained domain roles.
-- Canonical: admin-api.md § 카탈로그 · § 시스템 프리셋; data-model.md § Migration Strategy (TASK-MONO-794);
-- finance iam-integration.md § 업무 역할 (what each role opens).
--
-- The catalog (DomainRoleCatalog) replaced ('finance', 'FINANCE_OPERATOR') with five Finance business roles.
-- FINANCE_OPERATOR is now the ENTRY role: auth-service derives it when >= 1 Finance business role rides the token; it is
-- never assigned and opens no business API on its own. This file moves the DATA to match, so every existing operator
-- keeps the same API set. Same shape as V0057 (ERP, P6):
--   1. three Finance presets with FIXED set ids (INSERT IGNORE - same ids in every environment). finance-ledger-operate
--      carries NO FINANCE_LEDGER_CONTROL (owner decision Q1 - the control writes are given only through finance-full /
--      all-domains-full / legacy-compat or a tenant set);
--   2. every set holding ('finance', 'FINANCE_OPERATOR') - presets finance-full / all-domains-full / legacy-compat AND
--      every tenant set (a tenant set that held it was made as «Finance 전체 업무») - gains the five roles FIRST,
--      FINANCE_LEDGER_CONTROL included (Q1: existing operators keep period close and FX override), then the
--      FINANCE_OPERATOR rows are deleted. The order is the safety: no set is ever «Finance 0», not even mid-file;
--   3. the finance-full description stops saying «세분 미지원»;
--   4. partnership scopes (tenant_partnership.delegated_scope, tenant_partnership_participant.participant_scope -
--      JSON {"domains": [...], "roles": [...]}) get the same rewrite, guarded by JSON_CONTAINS (rows without the role
--      are not touched). auth carries delegated roles verbatim, so an unrewritten delegation would lose Finance at deploy.
--
-- Re-run = 0 rows (INSERT IGNORE on PKs · DELETE finds nothing · both UPDATEs are guarded). Forward-only. MySQL-only.
-- No cross-statement user variable (V0016 cycle-2 trap). No semicolon inside any literal (the ITs split on it).
-- Shape: V0058MigrationShapeTest · behaviour on real MySQL: V0058FinanceRoleMigrationIntegrationTest.

-- ========================================================================
-- 1. Presets (admin-api.md § 시스템 프리셋)
-- ========================================================================
INSERT IGNORE INTO domain_permission_set
    (set_id, tenant_id, system_key, name, description, created_by, created_at, updated_at, version)
VALUES
    ('00000083-0000-7000-8000-000000000024', NULL, 'finance-read',            'Finance 조회', 'Finance 계좌 · 원장 조회',            NULL, NOW(6), NOW(6), 0),
    ('00000083-0000-7000-8000-000000000025', NULL, 'finance-account-operate', '계좌 운영',    'Finance 계좌 조회 · 변경',            NULL, NOW(6), NOW(6), 0),
    ('00000083-0000-7000-8000-000000000026', NULL, 'finance-ledger-operate',  '원장 운영',    'Finance 원장 조회 · 업무 변경(통제 제외)', NULL, NOW(6), NOW(6), 0);

INSERT IGNORE INTO domain_permission_set_role (set_id, domain_key, role_name)
SELECT s.id, v.domain_key, v.role_name
  FROM domain_permission_set s
  JOIN (
        SELECT 'finance-read' AS system_key, 'finance' AS domain_key, 'FINANCE_ACCOUNT_READ' AS role_name
        UNION ALL SELECT 'finance-read',            'finance', 'FINANCE_LEDGER_READ'
        UNION ALL SELECT 'finance-account-operate', 'finance', 'FINANCE_ACCOUNT_READ'
        UNION ALL SELECT 'finance-account-operate', 'finance', 'FINANCE_ACCOUNT_WRITE'
        UNION ALL SELECT 'finance-ledger-operate',  'finance', 'FINANCE_LEDGER_READ'
        UNION ALL SELECT 'finance-ledger-operate',  'finance', 'FINANCE_LEDGER_WRITE'
       ) v ON v.system_key = s.system_key;

-- ========================================================================
-- 2. Every set holding FINANCE_OPERATOR: add the five roles, THEN delete FINANCE_OPERATOR
-- ========================================================================
INSERT IGNORE INTO domain_permission_set_role (set_id, domain_key, role_name)
SELECT h.set_id, 'finance', v.role_name
  FROM (SELECT DISTINCT set_id FROM domain_permission_set_role
         WHERE domain_key = 'finance' AND role_name = 'FINANCE_OPERATOR') h
  CROSS JOIN (
        SELECT 'FINANCE_ACCOUNT_READ' AS role_name
        UNION ALL SELECT 'FINANCE_ACCOUNT_WRITE'
        UNION ALL SELECT 'FINANCE_LEDGER_READ'
        UNION ALL SELECT 'FINANCE_LEDGER_WRITE'
        UNION ALL SELECT 'FINANCE_LEDGER_CONTROL'
       ) v;

DELETE FROM domain_permission_set_role WHERE domain_key = 'finance' AND role_name = 'FINANCE_OPERATOR';

-- ========================================================================
-- 3. finance-full description (guarded - a re-run matches 0 rows)
-- ========================================================================
UPDATE domain_permission_set
   SET description = 'Finance 카탈로그 역할 전부(계좌 · 원장 · 원장 통제)', updated_at = NOW(6)
 WHERE system_key = 'finance-full'
   AND description <> 'Finance 카탈로그 역할 전부(계좌 · 원장 · 원장 통제)';

-- ========================================================================
-- 4. Partnership scopes: FINANCE_OPERATOR -> the five roles (JSON_CONTAINS-guarded)
-- ========================================================================
UPDATE tenant_partnership
   SET delegated_scope = JSON_SET(delegated_scope, '$.roles',
           JSON_MERGE_PRESERVE(
               JSON_REMOVE(JSON_EXTRACT(delegated_scope, '$.roles'),
                           JSON_UNQUOTE(JSON_SEARCH(JSON_EXTRACT(delegated_scope, '$.roles'), 'one', 'FINANCE_OPERATOR'))),
               JSON_ARRAY('FINANCE_ACCOUNT_READ', 'FINANCE_ACCOUNT_WRITE', 'FINANCE_LEDGER_READ',
                          'FINANCE_LEDGER_WRITE', 'FINANCE_LEDGER_CONTROL'))),
       updated_at = NOW(6)
 WHERE JSON_CONTAINS(JSON_EXTRACT(delegated_scope, '$.roles'), '"FINANCE_OPERATOR"');

UPDATE tenant_partnership_participant
   SET participant_scope = JSON_SET(participant_scope, '$.roles',
           JSON_MERGE_PRESERVE(
               JSON_REMOVE(JSON_EXTRACT(participant_scope, '$.roles'),
                           JSON_UNQUOTE(JSON_SEARCH(JSON_EXTRACT(participant_scope, '$.roles'), 'one', 'FINANCE_OPERATOR'))),
               JSON_ARRAY('FINANCE_ACCOUNT_READ', 'FINANCE_ACCOUNT_WRITE', 'FINANCE_LEDGER_READ',
                          'FINANCE_LEDGER_WRITE', 'FINANCE_LEDGER_CONTROL')))
 WHERE participant_scope IS NOT NULL
   AND JSON_CONTAINS(JSON_EXTRACT(participant_scope, '$.roles'), '"FINANCE_OPERATOR"');
