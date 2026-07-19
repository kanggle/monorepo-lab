-- ADR-MONO-050 D9 / TASK-SCM-BE-037 — batch replenishment leg.
--
-- wms's three inventory mutation events (received / adjusted / transferred) now carry an
-- additive, nullable `warehouseCode` alongside the warehouse uuid, mirroring the existing
-- `inventory.low-stock-detected` alert field. Persisting it on the node read-model lets the
-- demand-planning batch sweep address a replenishment PO by warehouse CODE — cross-service
-- identifiers are codes, not uuids — so batch-origin POs can emit `inbound-expected.v1`
-- exactly like the live alert path already does.
--
-- Nullable by design (fail-closed): wms resolves the code best-effort from its warehouse
-- master read-model and emits null while that snapshot is unpopulated. A null code never
-- blocks node creation, snapshot projection, or a reorder suggestion — it only omits the
-- downstream inbound-expected addressing. A null incoming code likewise never overwrites a
-- previously stored non-null code (set-if-present semantics in InventoryNode).
ALTER TABLE inventory_nodes
    ADD COLUMN warehouse_code VARCHAR(100);

COMMENT ON COLUMN inventory_nodes.warehouse_code IS
    'Nullable business warehouse code learned from wms inventory mutation events (ADR-MONO-050 D9). Null until wms resolves it; never overwritten with null.';
