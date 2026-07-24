-- ADR-MONO-055 §D2/§D3 (TASK-SCM-BE-048) — inbound replenishment-target allocation
-- widens from "wms warehouse only" to "any observed node type."
--
-- reorder_suggestion gains a destination node TYPE alongside its warehouse dimensions.
-- The batch sweep populates it from the IVS read-model's nodeType, so a below-reorder
-- THIRD_PARTY_LOGISTICS node drafts a PO addressed to that 3PL node. The value flows to
-- the procurement PO destinationNodeType (already node-type-aware downstream).
--
-- Additive + nullable: pre-055 in-flight rows have no type. The domain normalises a null
-- to WMS_WAREHOUSE (backward compat, the pre-055 contract); the backfill below makes any
-- existing rows explicit as well. There are none at demo scale, but the UPDATE is safe.

ALTER TABLE reorder_suggestion
    ADD COLUMN destination_node_type VARCHAR(30);

UPDATE reorder_suggestion
    SET destination_node_type = 'WMS_WAREHOUSE'
    WHERE destination_node_type IS NULL;
