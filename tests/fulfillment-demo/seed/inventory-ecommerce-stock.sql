-- inventory-service stock seed for the ecommerce↔wms full fulfillment loop
-- (docker-compose.ecommerce-fulfillment.yml). The UUIDs MUST match what
-- outbound-service resolves for the ecommerce order line — i.e. the
-- outbound-readmodel.sql snapshot ids:
--   warehouse WH-MAIN = b0000000-0000-0000-0000-00000000a111
--   sku SKU-APPLE-001  = c0000000-0000-0000-0000-00005c0a9001 (tracking NONE, no lot)
-- so the `outbound.picking.requested` event (skuId=c0..9001, warehouseId=b0..a111,
-- lotId=null, locationId=null) resolves to this stock row via
-- InventoryRepository.findAvailableByWarehouseSkuLot (TASK-BE-431).
--
-- Apply AFTER inventory-service has started (its Flyway creates the tables).
-- Idempotent (ON CONFLICT DO NOTHING).

-- Master read-model snapshots (location + sku) keyed by the outbound UUIDs.
INSERT INTO location_snapshot (id, location_code, warehouse_id, zone_id, location_type, status, cached_at, master_version)
VALUES ('d0000000-0000-0000-0000-000000007011', 'WH-MAIN-A-01', 'b0000000-0000-0000-0000-00000000a111',
        'd0000000-0000-0000-0000-000000000101', 'STORAGE', 'ACTIVE', now(), 1)
ON CONFLICT (id) DO NOTHING;

INSERT INTO sku_snapshot (id, sku_code, tracking_type, base_uom, status, cached_at, master_version)
VALUES ('c0000000-0000-0000-0000-00005c0a9001', 'SKU-APPLE-001', 'NONE', 'EA', 'ACTIVE', now(), 1)
ON CONFLICT (id) DO NOTHING;

-- Physical stock row: 100 available, no lot, at the WH-MAIN location above.
INSERT INTO inventory (id, warehouse_id, location_id, sku_id, lot_id,
                       available_qty, reserved_qty, damaged_qty,
                       last_movement_at, version, created_at, created_by, updated_at, updated_by)
VALUES (gen_random_uuid(), 'b0000000-0000-0000-0000-00000000a111', 'd0000000-0000-0000-0000-000000007011',
        'c0000000-0000-0000-0000-00005c0a9001', NULL,
        100, 0, 0, now(), 0, now(), 'seed', now(), 'seed')
ON CONFLICT DO NOTHING;
