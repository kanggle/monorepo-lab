-- TASK-BE-363 (ADR-MONO-030 Step 3, inner marketplace axis — seller).
--
-- order-line attribution: each order_item carries the seller_id of the product it
-- was placed against (ADR-030 §3.2). The order HEADER stays tenant-only — seller
-- is per-LINE, so one order may span multiple sellers (shared catalog / cart). The
-- seller is supplied at placement as a denormalized snapshot (mirroring
-- product_name / unit_price); order-service does NOT call product-service.
--
-- default-seller (D8 / AC-5, symmetric with the default-tenant seed of V8): all
-- pre-marketplace lines are backfilled to seller_id='default' → today's
-- single-seller behaviour is byte-identical. Zero-downtime 2-step:
-- ADD nullable -> backfill 'default' -> SET NOT NULL.

-- ---- order_items.seller_id (per-line attribution) --------------------------
ALTER TABLE order_items ADD COLUMN seller_id VARCHAR(64);
UPDATE order_items SET seller_id = 'default' WHERE seller_id IS NULL;
ALTER TABLE order_items ALTER COLUMN seller_id SET NOT NULL;

-- Seller-scoped OPERATOR reads narrow to orders having a line for the seller; the
-- seller axis is always nested inside the tenant axis, so lead with tenant_id.
CREATE INDEX idx_order_items_tenant_seller ON order_items (tenant_id, seller_id);
