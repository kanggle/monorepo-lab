-- =============================================================================
-- V5 — TASK-BE-644: drop the stored low-stock flag from admin_inventory_snapshot
-- =============================================================================
-- The flag was computed at projection time against a hard-coded `<= 10` and
-- never followed the `inventory.low_stock.default_threshold_qty` setting, so a
-- setting change left every row without a new inventory event on the old
-- threshold. Low-stock is now derived at query time (`available_qty < :threshold`,
-- threshold = the current GLOBAL setting, fallback 10) — see
-- specs/services/admin-service/domain-model.md § InventorySnapshot.
--
-- No data is lost: the flag was a pure function of available_qty and a constant.
-- 🔴 Rolling the image back below this version after V5 has run would break the
--    projection (old code writes low_stock_flag). Roll forward instead.
-- =============================================================================

DROP INDEX IF EXISTS idx_admin_inventory_snapshot_low_stock;

ALTER TABLE admin_inventory_snapshot DROP COLUMN IF EXISTS low_stock_flag;
