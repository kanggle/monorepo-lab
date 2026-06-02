-- =============================================================================
-- TASK-MONO-170 — console full-stack DEMO seed — WMS master (warehouses)
-- =============================================================================
-- Applied against the per-project `wms-postgres` master_db AFTER wms
-- master-service is healthy (Flyway done):
--
--   docker exec -i wms-postgres psql -U postgres -d master_db < 04-wms-master.sql
--
-- Row reuses tests/federation-hardening-e2e/fixtures/seed-wms.sql verbatim.
-- warehouses is tenant-NEUTRAL (no tenant_id column — WMS enforces tenancy at
-- the JWT claim layer), so this single row serves any operator/active-tenant.
-- Feeds the WMS warehouse refs the overview + ops surfaces reference.
-- Re-runnable: ON CONFLICT (id) DO NOTHING.
-- =============================================================================

INSERT INTO warehouses (
    id, warehouse_code, name, address, timezone, status,
    version, created_at, created_by, updated_at, updated_by
) VALUES (
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'DEMO-WH-01', 'Demo Warehouse', '123 Demo Street, Demo City',
    'Asia/Seoul', 'ACTIVE', 0, NOW(), 'demo-seed', NOW(), 'demo-seed'
)
ON CONFLICT (id) DO NOTHING;
