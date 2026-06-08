-- TASK-MONO-200 — outbound-service read-model seed for the fulfillment forward-leg demo.
--
-- outbound-service resolves the ecommerce fulfillment event's codes -> uuids from
-- its OWN read-model snapshots (normally fed by wms master.* events; here master-
-- service is NOT run, so we seed the three snapshots directly — the exact recipe
-- FulfillmentRequestedConsumerIT.seedMaster() uses).
--
-- Apply AFTER outbound-service has started (its Flyway creates these tables):
--   docker compose -f projects/ecommerce-microservices-platform/docker-compose.yml \
--     -f tests/fulfillment-demo/docker-compose.fulfillment-demo.yml \
--     exec -T fulfillment-demo-outbound-postgres \
--     psql -U outbound -d outbound_db < tests/fulfillment-demo/seed/outbound-readmodel.sql
--
-- Idempotent (ON CONFLICT DO NOTHING) so re-running is safe.

INSERT INTO partner_snapshot (id, partner_code, partner_type, status, cached_at, master_version)
VALUES ('a0000000-0000-0000-0000-0000000000e5', 'ECOMMERCE-STORE', 'CUSTOMER', 'ACTIVE', now(), 1)
ON CONFLICT (id) DO NOTHING;

INSERT INTO warehouse_snapshot (id, warehouse_code, status, cached_at, master_version)
VALUES ('b0000000-0000-0000-0000-00000000a111', 'WH-MAIN', 'ACTIVE', now(), 1)
ON CONFLICT (id) DO NOTHING;

-- skuCode == the ecommerce order line's variantId (forward-ACL identity, D3).
-- Place demo orders with variantId 'SKU-APPLE-001' so this resolves.
INSERT INTO sku_snapshot (id, sku_code, tracking_type, status, cached_at, master_version)
VALUES ('c0000000-0000-0000-0000-00005c0a9001', 'SKU-APPLE-001', 'NONE', 'ACTIVE', now(), 1)
ON CONFLICT (id) DO NOTHING;
