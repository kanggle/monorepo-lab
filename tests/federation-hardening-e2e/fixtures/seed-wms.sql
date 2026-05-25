-- =============================================================================
-- TASK-MONO-139 — Phase 8 Federation Hardening e2e wms seed (phase 2.5)
-- =============================================================================
-- Applied against wms-postgres via psql AFTER wms-master-service is healthy.
-- PostgreSQL (wms uses PostgreSQL, not MySQL — application.yml confirms
-- jdbc:postgresql://.../master_db).
--
-- One warehouse row — minimum shape to satisfy wms-golden-path.spec.ts.
-- tenant_id is NOT a column on warehouses (WMS uses JWT tenant_id claim
-- enforcement, not a data-layer tenant column). The SUPER_ADMIN JWT with
-- tenant_id='*' is accepted by wms TenantClaimValidator. No tenant_id
-- column in warehouses table (V1__init_warehouse.sql).
--
-- Re-runnable: INSERT is idempotent via ON CONFLICT (id) DO NOTHING.
-- =============================================================================

INSERT INTO warehouses (
    id, warehouse_code, name, address, timezone, status,
    version, created_at, created_by, updated_at, updated_by
) VALUES (
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'E2E-WH-01',
    'E2E Test Warehouse',
    '123 Test Street, Test City',
    'Asia/Seoul',
    'ACTIVE',
    0,
    NOW(),
    'e2e-seed',
    NOW(),
    'e2e-seed'
)
ON CONFLICT (id) DO NOTHING;
