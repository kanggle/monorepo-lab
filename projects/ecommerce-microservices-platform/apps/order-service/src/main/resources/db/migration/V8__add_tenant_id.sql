-- TASK-BE-357 increment C (ADR-MONO-030 Step 2, outer tenant axis — M1).
--
-- Row-level tenant_id on every order-service persistent aggregate/entity
-- (orders, order_items). The outbox / processed_events tables are messaging
-- infrastructure and carry tenant on the event envelope payload, not as a
-- column (ADR-030 §2.3 M5, messaging standard) — they are intentionally left
-- unchanged.
--
-- Zero-downtime 3-step per table: ADD nullable -> backfill 'ecommerce'
-- (default-tenant, D8 net-zero) -> SET NOT NULL. All pre-existing rows belong to
-- the single implicit store, mapped to default tenant 'ecommerce'.

-- ---- orders -----------------------------------------------------------------
ALTER TABLE orders ADD COLUMN tenant_id VARCHAR(64);
UPDATE orders SET tenant_id = 'ecommerce' WHERE tenant_id IS NULL;
ALTER TABLE orders ALTER COLUMN tenant_id SET NOT NULL;
-- The user-facing list query filters by (tenant_id, user_id); lead with
-- tenant_id (every read is tenant-scoped). The admin/status-list path filters by
-- (tenant_id, status); the stuck-detector sweep stays tenant-agnostic (global,
-- operational) and keeps using idx_orders_status_created_at.
CREATE INDEX idx_orders_tenant_user ON orders (tenant_id, user_id);
CREATE INDEX idx_orders_tenant_status ON orders (tenant_id, status);

-- ---- order_items ------------------------------------------------------------
ALTER TABLE order_items ADD COLUMN tenant_id VARCHAR(64);
UPDATE order_items SET tenant_id = 'ecommerce' WHERE tenant_id IS NULL;
ALTER TABLE order_items ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_order_items_tenant ON order_items (tenant_id);
