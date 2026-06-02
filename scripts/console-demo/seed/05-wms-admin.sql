-- =============================================================================
-- TASK-MONO-170 — console full-stack DEMO seed — WMS admin (inventory snapshot)
-- =============================================================================
-- Applied against the per-project `wms-postgres` admin_db AFTER wms
-- admin-service is healthy (Flyway done):
--
--   docker exec -i wms-postgres psql -U postgres -d admin_db < 05-wms-admin.sql
--
-- Row reuses tests/federation-hardening-e2e/fixtures/seed-wms-admin.sql verbatim
-- (admin_inventory_snapshot — the read-model GET /api/v1/admin/dashboard/inventory
-- reads). Tenant-NEUTRAL (no tenant_id column), so a single row yields a
-- non-empty 200 for any active-tenant (acme-corp for the demo's WMS leg).
--
-- NOTE — the **WMS 운영** page ALSO has an alerts section (GET /dashboard/alerts).
-- That read-model (admin alerts table) is NOT seeded here (the fed-e2e overview
-- never exercised it). If the alerts section shows a degraded/empty state in the
-- live demo, that is expected for now — the inventory section is the primary WMS
-- ops proof. Seeding alerts is a documented follow-up (needs the alerts table
-- DDL from wms admin-service migrations).
--
-- Re-runnable: ON CONFLICT (location_id, sku_id, lot_id) DO NOTHING.
-- =============================================================================

INSERT INTO admin_inventory_snapshot (
    location_id, sku_id, lot_id, warehouse_id,
    location_code, sku_code, lot_no,
    available_qty, reserved_qty, damaged_qty, on_hand_qty,
    low_stock_flag, last_adjusted_at, last_event_at, version
) VALUES (
    'b0000000-0000-0000-0000-000000000001'::uuid,
    'c0000000-0000-0000-0000-000000000001'::uuid,
    '00000000-0000-0000-0000-000000000000'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'DEMO-LOC-01', 'DEMO-SKU-01', NULL,
    100, 0, 0, 100,
    FALSE, NOW(), NOW(), 0
)
ON CONFLICT (location_id, sku_id, lot_id) DO NOTHING;
