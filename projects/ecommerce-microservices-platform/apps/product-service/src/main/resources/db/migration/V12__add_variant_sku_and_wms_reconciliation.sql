-- TASK-MONO-198 (ADR-MONO-022 §D4 v2(b)) — inventory reconciliation support.
--
-- 1. ProductVariant gains a `sku` business key (it previously had a UUID id only).
--    `sku == wms skuCode` is the resolution bridge for warehouse-origin reconciliation.
-- 2. Three reconciliation tables for product-service's FIRST inbound consumer:
--    - wms_sku_snapshot:        skuId(uuid) -> skuCode (built from wms.master.sku.v1)
--    - wms_inventory_available: per-inventoryId availableQty trajectory (the delta ledger)
--    - wms_processed_event:     idempotent-consumer dedupe on the wms envelope eventId (T8)

-- ---- 1. variant SKU business key -------------------------------------------
ALTER TABLE product_variants
    ADD COLUMN sku VARCHAR(64);

-- Deterministic backfill of the seed variants (last 12 hex of the id).
-- Live wms↔ecommerce skuCode alignment is a seed-data concern (v2(b) follow-up).
UPDATE product_variants
   SET sku = 'SKU-EC-' || right(replace(id::text, '-', ''), 12)
 WHERE sku IS NULL;

-- UNIQUE allows multiple NULLs (variants without a SKU simply do not reconcile).
ALTER TABLE product_variants
    ADD CONSTRAINT uq_product_variants_sku UNIQUE (sku);

-- ---- 2a. skuId -> skuCode snapshot (reverse identity) ----------------------
CREATE TABLE wms_sku_snapshot (
    sku_id     UUID         NOT NULL,
    sku_code   VARCHAR(64)  NOT NULL,
    version    BIGINT       NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_wms_sku_snapshot PRIMARY KEY (sku_id)
);
CREATE INDEX idx_wms_sku_snapshot_sku_code ON wms_sku_snapshot (sku_code);

-- ---- 2b. per-inventoryId availableQty trajectory (the delta ledger) --------
CREATE TABLE wms_inventory_available (
    inventory_id  UUID        NOT NULL,
    sku_id        UUID        NOT NULL,
    available_qty INT         NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_wms_inventory_available PRIMARY KEY (inventory_id)
);
CREATE INDEX idx_wms_inventory_available_sku_id ON wms_inventory_available (sku_id);

-- ---- 2c. idempotent-consumer dedupe (T8) ----------------------------------
CREATE TABLE wms_processed_event (
    event_id     UUID         NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_wms_processed_event PRIMARY KEY (event_id)
);
