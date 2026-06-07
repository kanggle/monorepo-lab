-- TASK-BE-340 (ADR-MONO-022 D2-a): additive drop-ship recipient columns on the
-- outbound order. Populated only for FULFILLMENT_ECOMMERCE-origin orders
-- (B2C drop-ship); NULL for MANUAL / WEBHOOK_ERP (B2B). All nullable — existing
-- rows and the ERP-webhook / manual paths are unaffected.

ALTER TABLE outbound_order ADD COLUMN ship_to_name    VARCHAR(200);
ALTER TABLE outbound_order ADD COLUMN ship_to_address VARCHAR(1000);
ALTER TABLE outbound_order ADD COLUMN ship_to_phone   VARCHAR(20);
