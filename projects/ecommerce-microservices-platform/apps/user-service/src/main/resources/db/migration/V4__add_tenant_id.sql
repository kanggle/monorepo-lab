-- TASK-BE-367 (ADR-MONO-030 Step 4 / ADR-MONO-031 Phase 2a, outer tenant axis — M1).
--
-- Row-level tenant_id on every user-service persistent entity (user_profiles,
-- user_addresses, wishlist_items). The child tables (user_addresses,
-- wishlist_items) denormalize tenant_id from their parent user_profiles; their
-- existing FK to user_profiles(user_id) and unique constraints are unchanged
-- (the single default-tenant backfill cannot introduce a conflict). user_id stays
-- globally unique (uq_user_profiles_user_id) — an IAM user belongs to one tenant;
-- tenant_id is the isolation column, not part of the identity key.
--
-- Zero-downtime 3-step per table: ADD nullable -> backfill 'ecommerce'
-- (default-tenant, D8 net-zero) -> SET NOT NULL. All pre-existing rows belong to
-- the single implicit store, mapped to default tenant 'ecommerce'.

-- ---- user_profiles ---------------------------------------------------------
ALTER TABLE user_profiles ADD COLUMN tenant_id VARCHAR(64);
UPDATE user_profiles SET tenant_id = 'ecommerce' WHERE tenant_id IS NULL;
ALTER TABLE user_profiles ALTER COLUMN tenant_id SET NOT NULL;
-- The admin/operator list query filters by (tenant_id, status) with an email
-- LIKE predicate; lead with tenant_id (every read is tenant-scoped).
CREATE INDEX idx_user_profiles_tenant_status ON user_profiles (tenant_id, status);

-- ---- user_addresses --------------------------------------------------------
ALTER TABLE user_addresses ADD COLUMN tenant_id VARCHAR(64);
UPDATE user_addresses SET tenant_id = 'ecommerce' WHERE tenant_id IS NULL;
ALTER TABLE user_addresses ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_user_addresses_tenant_user ON user_addresses (tenant_id, user_id);

-- ---- wishlist_items --------------------------------------------------------
ALTER TABLE wishlist_items ADD COLUMN tenant_id VARCHAR(64);
UPDATE wishlist_items SET tenant_id = 'ecommerce' WHERE tenant_id IS NULL;
ALTER TABLE wishlist_items ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_wishlist_items_tenant_user ON wishlist_items (tenant_id, user_id);
