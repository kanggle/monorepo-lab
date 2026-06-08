-- TASK-BE-340/342 (ADR-MONO-022): outbound_order.source was VARCHAR(20) (V10,
-- sized for MANUAL / WEBHOOK_ERP). The cross-project fulfillment source value
-- 'FULFILLMENT_ECOMMERCE' is 21 chars → INSERT failed with
-- "value too long for type character varying(20)", so an ecommerce-origin
-- outbound order could never be persisted. Widen to VARCHAR(50) (matching the
-- webhook-inbox source column) with headroom for future sources.

ALTER TABLE outbound_order ALTER COLUMN source TYPE VARCHAR(50);
