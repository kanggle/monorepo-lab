-- TASK-BE-357 increment B (ADR-MONO-030 Step 2, outer tenant axis — M1).
--
-- Row-level tenant_id on every product-service persistent aggregate/entity
-- (products, product_variants, categories). The wms_* reconciliation tables are
-- messaging/projection infrastructure (no tenant column — reconciliation resolves
-- to the default tenant; ADR-030 §2.3 M5 carries tenant in the event envelope).
--
-- Zero-downtime 3-step per table: ADD nullable -> backfill 'ecommerce'
-- (default-tenant, D8 net-zero) -> SET NOT NULL. All pre-existing rows belong to
-- the single implicit store, mapped to default tenant 'ecommerce'.

-- ---- products --------------------------------------------------------------
ALTER TABLE products ADD COLUMN tenant_id VARCHAR(64);
UPDATE products SET tenant_id = 'ecommerce' WHERE tenant_id IS NULL;
ALTER TABLE products ALTER COLUMN tenant_id SET NOT NULL;
-- List/detail query path filters by (tenant_id, category_id, status) with the
-- deleted_at predicate; lead with tenant_id (every read is tenant-scoped).
CREATE INDEX idx_products_tenant ON products (tenant_id, category_id, status);

-- ---- product_variants ------------------------------------------------------
ALTER TABLE product_variants ADD COLUMN tenant_id VARCHAR(64);
UPDATE product_variants SET tenant_id = 'ecommerce' WHERE tenant_id IS NULL;
ALTER TABLE product_variants ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_product_variants_tenant ON product_variants (tenant_id);

-- SKU uniqueness moves from global to per-tenant scope: two tenants may legally
-- reuse the same SKU string. Backfill is a single default tenant, so widening the
-- key cannot introduce a conflict. Composite UNIQUE keeps multi-NULL semantics
-- (variants without a SKU still do not reconcile).
ALTER TABLE product_variants DROP CONSTRAINT uq_product_variants_sku;
ALTER TABLE product_variants ADD CONSTRAINT uq_product_variants_sku UNIQUE (tenant_id, sku);

-- ---- categories ------------------------------------------------------------
ALTER TABLE categories ADD COLUMN tenant_id VARCHAR(64);
UPDATE categories SET tenant_id = 'ecommerce' WHERE tenant_id IS NULL;
ALTER TABLE categories ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_categories_tenant ON categories (tenant_id);
