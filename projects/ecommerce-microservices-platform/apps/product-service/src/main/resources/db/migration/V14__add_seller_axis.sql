-- TASK-BE-363 (ADR-MONO-030 Step 3, inner marketplace axis — seller).
--
-- Adds the inner seller axis nested under the Step-2 tenant_id axis:
--   1. a net-new `sellers` aggregate keyed (tenant_id, seller_id);
--   2. `products.seller_id NOT NULL` — product ownership = (tenant_id, seller_id).
--
-- Seller is a participant INSIDE a tenant (ADR-030 D3-B rejected), so the seller
-- filter is always nested within the tenant filter (isolate-then-attribute, AC-6).
-- categories are NOT seller-owned: they are shared catalog taxonomy, so no
-- seller_id there.
--
-- default-seller (D8 / AC-5, symmetric with the default-tenant seed of V13): every
-- existing tenant gets a single seller_id='default', and all pre-marketplace
-- products are backfilled to it → today's single-seller behaviour is byte-identical.
-- Zero-downtime 2-step on products: ADD nullable -> seed sellers + backfill 'default'
-- -> SET NOT NULL.

-- ---- sellers (net-new aggregate) -------------------------------------------
CREATE TABLE sellers (
    tenant_id    VARCHAR(64)  NOT NULL,
    seller_id    VARCHAR(64)  NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at   TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT pk_sellers PRIMARY KEY (tenant_id, seller_id)
);

-- Seed one default seller per tenant already present in products (at minimum the
-- default tenant 'ecommerce'). The single backfill tenant set keeps this bounded.
INSERT INTO sellers (tenant_id, seller_id, display_name, status)
SELECT DISTINCT p.tenant_id, 'default', 'Default Seller', 'ACTIVE'
FROM products p
ON CONFLICT (tenant_id, seller_id) DO NOTHING;

-- Always ensure the default tenant's default seller exists, even when products is
-- empty (fresh install) — the standalone single-seller anchor.
INSERT INTO sellers (tenant_id, seller_id, display_name, status)
VALUES ('ecommerce', 'default', 'Default Seller', 'ACTIVE')
ON CONFLICT (tenant_id, seller_id) DO NOTHING;

-- ---- products.seller_id (ownership) ----------------------------------------
ALTER TABLE products ADD COLUMN seller_id VARCHAR(64);
UPDATE products SET seller_id = 'default' WHERE seller_id IS NULL;
ALTER TABLE products ALTER COLUMN seller_id SET NOT NULL;

-- Seller-scoped OPERATOR reads filter by (tenant_id, seller_id) — the seller axis
-- is always nested inside the tenant axis, so lead with tenant_id.
CREATE INDEX idx_products_tenant_seller ON products (tenant_id, seller_id);
