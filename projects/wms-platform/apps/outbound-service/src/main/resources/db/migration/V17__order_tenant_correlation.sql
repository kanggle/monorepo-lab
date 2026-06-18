-- TASK-MONO-296 (ADR-MONO-022 facet d): additive, NULLABLE tenant correlation
-- column on the outbound order. Populated only for FULFILLMENT_ECOMMERCE-origin
-- orders from the inbound ecommerce.fulfillment.requested.v1 envelope's tenantId,
-- and echoed back unchanged onto the return-leg events
-- (wms.outbound.shipping.confirmed.v1 / wms.outbound.order.cancelled.v1).
--
-- This is an OPAQUE CORRELATION FIELD alongside order_no (D5) and ship_to_* (D2-a)
-- — NOT a tenant-isolation key. wms stays single-tenant (ADR-MONO-030 §1.1):
--   * NULLABLE by design — NO NOT NULL constraint (B2B / pre-M5 / standalone rows
--     carry NULL; existing rows and the ERP-webhook / manual paths are unaffected).
--   * NO index, NO WHERE tenant_id filtering, NO tenant gate change — wms never
--     queries or partitions by this column.

ALTER TABLE outbound_order ADD COLUMN tenant_id VARCHAR(64);

COMMENT ON COLUMN outbound_order.tenant_id IS
    'ADR-MONO-022 facet d: opaque ecommerce tenant correlation echoed on return-leg events. NOT an isolation key; wms stays single-tenant. NULL for B2B/standalone.';
