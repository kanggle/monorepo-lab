-- =============================================================================
-- TASK-MONO-170 — console full-stack DEMO seed — ERP (globex-corp)
-- =============================================================================
-- Applied against the per-project `erp-platform-mysql` (erp_db) AFTER erp
-- masterdata-service is healthy (Flyway done):
--
--   docker exec -i erp-platform-mysql mysql -uroot -proot erp_db < 03-erp.sql
--
-- Rows reuse tests/federation-hardening-e2e/fixtures/seed-domains.sql (erp
-- section) but are RE-SCOPED to tenant_id='globex-corp' (NOT the fixture's '*').
-- Rationale: the ERP masterdata read-model is tenant-FILTERED (departments etc.
-- carry tenant_id). The **ERP 운영** page is reachable only when the active
-- tenant entitles erp — i.e. globex-corp ([scm,erp]). The globex assumed token
-- carries tenant_id='globex-corp'; the controller queries that tenant, so the
-- rows must be globex-corp-scoped to render non-empty. (erp masterdata
-- dual-accepts the entitled_domains ∋ erp claim per MONO-161/162; the DATA scope
-- is the tenant_id column, which must match.)
--
-- 5 masters minimal shape (department → cost_center → job_grade → employee;
-- business_partners optional). effective_from past, effective_to NULL (active).
-- Re-runnable: INSERT IGNORE.
-- =============================================================================
USE `erp_db`;

INSERT IGNORE INTO departments (
    id, tenant_id, code, name, parent_id, status,
    effective_from, effective_to, created_at, updated_at, version
) VALUES (
    'demo-dept-globex-001', 'globex-corp', 'ROOT', 'Globex Root Department',
    NULL, 'ACTIVE', '2020-01-01', NULL, NOW(6), NOW(6), 0
);

INSERT IGNORE INTO cost_centers (
    id, tenant_id, code, name, department_id, status,
    effective_from, effective_to, created_at, updated_at, version
) VALUES (
    'demo-cc-globex-001', 'globex-corp', 'CC-ROOT', 'Globex Root Cost Center',
    'demo-dept-globex-001', 'ACTIVE', '2020-01-01', NULL, NOW(6), NOW(6), 0
);

INSERT IGNORE INTO job_grades (
    id, tenant_id, code, name, display_order, status,
    effective_from, effective_to, created_at, updated_at, version
) VALUES (
    'demo-jg-globex-001', 'globex-corp', 'JG-L1', 'Globex Job Grade L1',
    1, 'ACTIVE', '2020-01-01', NULL, NOW(6), NOW(6), 0
);

INSERT IGNORE INTO employees (
    id, tenant_id, employee_number, name,
    department_id, cost_center_id, job_grade_id, status,
    effective_from, effective_to, created_at, updated_at, version
) VALUES (
    'demo-emp-globex-001', 'globex-corp', 'EMP-0001', 'Globex Demo Employee',
    'demo-dept-globex-001', 'demo-cc-globex-001', 'demo-jg-globex-001', 'ACTIVE',
    '2020-01-01', NULL, NOW(6), NOW(6), 0
);
