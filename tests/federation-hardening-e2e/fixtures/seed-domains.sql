-- =============================================================================
-- TASK-MONO-139 — Phase 8 Federation Hardening e2e harness domain seed (phase 2.5)
-- =============================================================================
-- Per-domain producer test data. Executed AFTER all producer services are
-- healthy (Flyway migrations complete) and BEFORE Playwright launches.
--
-- Mirrors the TASK-MONO-132 phase-2.5 split pattern (finance seed moved
-- from seed.sql to seed-finance.sql after Flyway). Applied per producer:
--   - finance_db: accounts + balances (reuse platform-console seed-finance.sql)
--   - erp_db: departments + employees (MySQL via erp-masterdata-service Flyway)
--   - wms_db: warehouses (PostgreSQL via wms-postgres sidecar — NOT in this file)
--   - scm_procurement_db: purchase_orders (PostgreSQL — NOT in this file)
--
-- Note: wms (PostgreSQL) and scm (PostgreSQL) domain seeds are applied via
-- separate psql commands in the workflow (see federation-hardening-e2e.yml
-- steps "Apply wms seed" and "Apply scm seed").
--
-- Re-runnable: every statement is idempotent (INSERT IGNORE /
-- ON DUPLICATE KEY UPDATE). The CI workflow runs seed-domains.sql exactly
-- once per spin-up.
--
-- HARDSTOP-04 invariant: this file does runtime INSERTs against producer
-- schemas. Neither the producer specs nor console-integration-contract.md
-- § 2.4.5/6/7/8 is modified on disk. Zero-retrofit sixth confirmation (ADR-018 § 3.1).
-- =============================================================================

-- ---------------------------------------------------------------------------
-- finance_db — single account + balance row.
--    Reuse platform-console seed-finance.sql pattern verbatim.
--    tenant_id='*' (platform-scope sentinel per TASK-BE-312).
--    owner_ref is AES-256-GCM encrypted (same key as docker-compose overlay).
-- ---------------------------------------------------------------------------
USE `finance_db`;

INSERT IGNORE INTO accounts (
    id, tenant_id, owner_ref, status, kyc_level, currency,
    created_at, updated_at, version
) VALUES (
    '01928c4a-7e9f-7c00-9a40-d2b1f5e8a000',
    '*',
    'v1:Al7AbOFq84oJ2wYqG+RB7CulHFYrnpNnNjp55iEWoJqqvscRZPN9mW46xrgq4w==',
    'ACTIVE',
    'FULL',
    'KRW',
    NOW(6), NOW(6), 0
);

INSERT IGNORE INTO balances (
    id, account_id, tenant_id, currency,
    ledger_minor, held_minor,
    created_at, updated_at, version
) VALUES (
    '01928c4a-7e9f-7c00-9a40-d2b1f5e8b001',
    '01928c4a-7e9f-7c00-9a40-d2b1f5e8a000',
    '*',
    'KRW',
    1000000, 0,
    NOW(6), NOW(6), 0
);

-- ---------------------------------------------------------------------------
-- TASK-MONO-154 — acme-corp finance account + balance.
--    Real-customer finance data scoped to tenant_id='acme-corp', matching the
--    admin_operators.finance_default_account_id seeded in seed.sql section 7
--    ('01928c4a-7e9f-7c00-9a40-d2b1f5e8a200'). This makes the finance leg
--    return a real 200 (balance present) for the acme-corp operator instead of
--    MISSING_PREREQUISITE — proving entitlement-trust ACCEPT with live data.
--    The acme-corp JWT carries tenant_id='acme-corp' + entitled_domains with
--    finance; finance-account-service TenantClaimValidator dual-accepts the
--    entitled_domains claim (ADR-019 step 3 gate).
--    owner_ref reuses the same AES-256-GCM envelope as the SUPER_ADMIN demo row
--    (opaque encrypted blob; the overview finance card surfaces balance, not a
--    decrypted owner_ref — same key as the docker-compose overlay).
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO accounts (
    id, tenant_id, owner_ref, status, kyc_level, currency,
    created_at, updated_at, version
) VALUES (
    '01928c4a-7e9f-7c00-9a40-d2b1f5e8a200',
    'acme-corp',
    'v1:Al7AbOFq84oJ2wYqG+RB7CulHFYrnpNnNjp55iEWoJqqvscRZPN9mW46xrgq4w==',
    'ACTIVE',
    'FULL',
    'KRW',
    NOW(6), NOW(6), 0
);

INSERT IGNORE INTO balances (
    id, account_id, tenant_id, currency,
    ledger_minor, held_minor,
    created_at, updated_at, version
) VALUES (
    '01928c4a-7e9f-7c00-9a40-d2b1f5e8b201',
    '01928c4a-7e9f-7c00-9a40-d2b1f5e8a200',
    'acme-corp',
    'KRW',
    500000, 0,
    NOW(6), NOW(6), 0
);

-- ---------------------------------------------------------------------------
-- erp_db — department + employee + cost_center rows (masterdata-service Flyway).
--    One row per aggregate — minimum shape to satisfy erp-golden-path.spec.ts.
--    effective_from=past, effective_to=NULL (currently active per E2 spec).
--    tenant_id='*' (SUPER_ADMIN wildcard accepted by TenantClaimValidator).
-- ---------------------------------------------------------------------------
USE `erp_db`;

INSERT IGNORE INTO departments (
    id, tenant_id, code, name, parent_id, status,
    effective_from, effective_to,
    created_at, updated_at, version
) VALUES (
    'e2e-dept-001',
    '*',
    'ROOT',
    'E2E Root Department',
    NULL,
    'ACTIVE',
    '2020-01-01',
    NULL,
    NOW(6), NOW(6), 0
);

INSERT IGNORE INTO cost_centers (
    id, tenant_id, code, name, department_id, status,
    effective_from, effective_to,
    created_at, updated_at, version
) VALUES (
    'e2e-cc-001',
    '*',
    'CC-ROOT',
    'E2E Root Cost Center',
    'e2e-dept-001',
    'ACTIVE',
    '2020-01-01',
    NULL,
    NOW(6), NOW(6), 0
);

INSERT IGNORE INTO job_grades (
    id, tenant_id, code, name, display_order, status,
    effective_from, effective_to,
    created_at, updated_at, version
) VALUES (
    'e2e-jg-001',
    '*',
    'JG-L1',
    'E2E Job Grade L1',
    1,
    'ACTIVE',
    '2020-01-01',
    NULL,
    NOW(6), NOW(6), 0
);

-- Per V1__init.sql line 38-55: employees columns are
--   id, tenant_id, employee_number, name, department_id, cost_center_id,
--   job_grade_id, status, effective_from, effective_to, created_at,
--   updated_at, version
-- (NOT full_name; cycle 3 fix corrects column name + adds required status).
INSERT IGNORE INTO employees (
    id, tenant_id, employee_number, name,
    department_id, cost_center_id, job_grade_id, status,
    effective_from, effective_to,
    created_at, updated_at, version
) VALUES (
    'e2e-emp-001',
    '*',
    'EMP-0001',
    'E2E Test Employee',
    'e2e-dept-001',
    'e2e-cc-001',
    'e2e-jg-001',
    'ACTIVE',
    '2020-01-01',
    NULL,
    NOW(6), NOW(6), 0
);
