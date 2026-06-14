-- TASK-BE-368 (ADR-MONO-030 Step 4 / ADR-MONO-031 Phase 3a, outer tenant axis — M1).
--
-- Row-level tenant_id on every promotion-service persistent aggregate/entity
-- (promotions, coupons). The outbox / processed_events tables are messaging
-- infrastructure and carry tenant on the event envelope payload, not as a
-- column (ADR-030 §2.3 M5, messaging standard) — they are intentionally left
-- unchanged.
--
-- Zero-downtime 3-step per table: ADD nullable -> backfill 'ecommerce'
-- (default-tenant, D8 net-zero) -> SET NOT NULL. All pre-existing rows belong to
-- the single implicit store, mapped to default tenant 'ecommerce'.

-- ---- promotions -------------------------------------------------------------
ALTER TABLE promotions ADD COLUMN tenant_id VARCHAR(64);
UPDATE promotions SET tenant_id = 'ecommerce' WHERE tenant_id IS NULL;
ALTER TABLE promotions ALTER COLUMN tenant_id SET NOT NULL;
-- The admin/operator status-list path derives ACTIVE/SCHEDULED/ENDED from
-- (start_date, end_date) — there is no status column. Lead with tenant_id (every
-- read is tenant-scoped), then the date window the status predicates filter on.
CREATE INDEX idx_promotions_tenant_status ON promotions (tenant_id, start_date, end_date);

-- ---- coupons ----------------------------------------------------------------
ALTER TABLE coupons ADD COLUMN tenant_id VARCHAR(64);
UPDATE coupons SET tenant_id = 'ecommerce' WHERE tenant_id IS NULL;
ALTER TABLE coupons ALTER COLUMN tenant_id SET NOT NULL;
-- The consumer "my coupons" read filters by (tenant_id, user_id); lead with
-- tenant_id (every read is tenant-scoped). The global expiry sweep
-- (status, expires_at) and the OrderCancelled recovery (order_id, unique) stay
-- tenant-agnostic and keep using their existing indexes.
CREATE INDEX idx_coupons_tenant_user ON coupons (tenant_id, user_id);
