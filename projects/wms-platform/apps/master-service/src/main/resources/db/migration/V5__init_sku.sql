-- SKU master data table. SKUs are independent aggregates (no parent refs); see
-- specs/services/master-service/domain-model.md §4 SKU and rules/domains/wms.md
-- for the authoritative invariants.
--
-- Invariants enforced at schema level:
--   - sku_code is globally unique, stored UPPERCASE only (CHECK guard). Matches
--     specs/contracts/http/master-service-api.md §4.1 case-insensitive rule.
--   - barcode is optional but unique when present — partial unique index.
--     Multiple rows with NULL barcode are permitted (independent SKUs without a
--     scannable code coexist peacefully).
--   - base_uom restricted to the enum values declared in BaseUom.java
--   - tracking_type restricted to the enum values declared in TrackingType.java
--   - status restricted to ACTIVE / INACTIVE (soft deactivation only)
--   - all audit columns are NOT NULL (created_at/by + updated_at/by always set)

CREATE TABLE skus (
    id              UUID         PRIMARY KEY,
    sku_code        VARCHAR(40)  NOT NULL,
    name            VARCHAR(200) NOT NULL,
    description     VARCHAR(1000),
    barcode         VARCHAR(40),
    base_uom        VARCHAR(10)  NOT NULL,
    tracking_type   VARCHAR(10)  NOT NULL,
    weight_grams    INTEGER,
    volume_ml       INTEGER,
    hazard_class    VARCHAR(20),
    shelf_life_days INTEGER,
    status          VARCHAR(20)  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL,
    created_by      VARCHAR(100) NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    updated_by      VARCHAR(100) NOT NULL,
    CONSTRAINT uq_skus_sku_code UNIQUE (sku_code),
    CONSTRAINT ck_skus_sku_code_uppercase CHECK (sku_code = UPPER(sku_code)),
    CONSTRAINT ck_skus_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_skus_base_uom CHECK (base_uom IN ('EA', 'BOX', 'PLT', 'KG', 'L')),
    CONSTRAINT ck_skus_tracking_type CHECK (tracking_type IN ('NONE', 'LOT')),
    CONSTRAINT ck_skus_weight_grams_nonneg CHECK (weight_grams IS NULL OR weight_grams >= 0),
    CONSTRAINT ck_skus_volume_ml_nonneg CHECK (volume_ml IS NULL OR volume_ml >= 0),
    CONSTRAINT ck_skus_shelf_life_days_nonneg CHECK (shelf_life_days IS NULL OR shelf_life_days >= 0)
);

-- Partial unique index on barcode — unique when non-null, many nulls allowed.
-- Both Postgres and H2 2.x support this syntax. JPA @UniqueConstraint cannot
-- express the WHERE filter, so this is DB-only (no entity-level annotation).
CREATE UNIQUE INDEX uq_skus_barcode ON skus (barcode) WHERE barcode IS NOT NULL;

-- List queries typically filter by status and sort by updated_at desc
-- (per specs/contracts/http/master-service-api.md §4.3 default sort).
CREATE INDEX idx_skus_status_updated_at
    ON skus (status, updated_at DESC);
