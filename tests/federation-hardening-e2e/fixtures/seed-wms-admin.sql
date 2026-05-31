-- =============================================================================
-- TASK-MONO-162 — Federation Hardening e2e wms admin-service seed (phase 2.5)
-- =============================================================================
-- Applied against wms-admin-postgres via psql AFTER wms-admin-service is healthy.
-- PostgreSQL (admin_db — application.yml confirms
-- jdbc:postgresql://.../admin_db). admin-service uses admin_db, NOT
-- master-service's master_db, so it has its own postgres sidecar.
--
-- Read-model the GET /api/v1/admin/dashboard/inventory endpoint reads:
--   admin_inventory_snapshot — the primary inventory dashboard read-model.
-- Schema derived from db/migration/V2__init_readmodel.sql § 11.
--
-- The table is tenant-NEUTRAL (no tenant_id column — wms enforces tenancy at
-- the JWT claim layer, not the data layer). A single row therefore suffices for
-- a non-empty 200 regardless of which customer's assumed token (acme-corp for
-- the A-side wms card) reads it. lot_id uses the sentinel UUID
-- '00000000-0000-0000-0000-000000000000' for the non-LOT-tracked row, matching
-- the V2 admin_setting/composite-PK pattern.
--
-- Re-runnable: INSERT is idempotent via ON CONFLICT (location_id, sku_id, lot_id)
-- DO NOTHING (the composite PK).
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
    'E2E-LOC-01',
    'E2E-SKU-01',
    NULL,
    100,
    0,
    0,
    100,
    FALSE,
    NOW(),
    NOW(),
    0
)
ON CONFLICT (location_id, sku_id, lot_id) DO NOTHING;
