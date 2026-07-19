-- TASK-SCM-BE-035 (ADR-MONO-050 D1/D3/D4) — carry the wms inbound-expected
-- addressing onto the PO so the CONFIRMED transition can publish
-- `scm.procurement.inbound-expected.v1`.
--
-- All three columns are nullable and additive:
--   * destination_warehouse_id — the warehouse that seeded the reorder
--     suggestion (ADR-050 D3, "addressed, not assumed"). NULL for
--     operator-authored POs, which are NOT turned into wms expectations
--     (fail-closed: no warehouse → no emit).
--   * destination_node_type     — v1 stores only 'WMS_WAREHOUSE'; the column
--     exists so a future 'THIRD_PARTY_LOGISTICS' value (ADR-050 D4) is a data
--     fact, not a schema change. The producer-side 3PL filter reads it.
--   * lead_time_days            — sku_supplier_map.lead_time_days carried at
--     materialization; expectedArrivalDate = confirmed_at + lead_time_days.
--
-- Populated only via the demand-planning `from-suggestion` path; existing
-- operator rows backfill to all-NULL (they never emit inbound-expected).

ALTER TABLE purchase_orders
    ADD COLUMN destination_warehouse_id VARCHAR(36),
    ADD COLUMN destination_node_type    VARCHAR(30),
    ADD COLUMN lead_time_days           INTEGER;

ALTER TABLE purchase_orders
    ADD CONSTRAINT ck_purchase_orders_destination_node_type
        CHECK (destination_node_type IS NULL
               OR destination_node_type IN ('WMS_WAREHOUSE', 'THIRD_PARTY_LOGISTICS'));

ALTER TABLE purchase_orders
    ADD CONSTRAINT ck_purchase_orders_lead_time_days
        CHECK (lead_time_days IS NULL OR lead_time_days >= 0);
