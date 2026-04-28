-- StockAdjustment and StockTransfer aggregates.
-- Authoritative reference: specs/services/inventory-service/domain-model.md §4 §5
--
-- Both aggregates are immutable post-create — no updates, no deletes via
-- application code path. The domain method is responsible for writing exactly
-- one (StockAdjustment + 1..2 InventoryMovement rows) or one (StockTransfer +
-- exactly 2 InventoryMovement rows) in the same transaction.

CREATE TABLE stock_adjustment (
    id              UUID         PRIMARY KEY,
    inventory_id    UUID         NOT NULL REFERENCES inventory (id),
    bucket          VARCHAR(20)  NOT NULL,
    delta           INTEGER      NOT NULL,
    reason_code     VARCHAR(40)  NOT NULL,
    reason_note     VARCHAR(500) NOT NULL,
    actor_id        VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(100),
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL,
    created_by      VARCHAR(100) NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    updated_by      VARCHAR(100) NOT NULL,
    CONSTRAINT ck_adjustment_bucket
        CHECK (bucket IN ('AVAILABLE', 'RESERVED', 'DAMAGED')),
    CONSTRAINT ck_adjustment_delta_nonzero
        CHECK (delta <> 0),
    CONSTRAINT ck_adjustment_reason_note_length
        CHECK (length(trim(reason_note)) >= 3)
);

CREATE INDEX idx_adjustment_inventory_created
    ON stock_adjustment (inventory_id, created_at DESC);

CREATE INDEX idx_adjustment_created
    ON stock_adjustment (created_at DESC);

CREATE TABLE stock_transfer (
    id                   UUID         PRIMARY KEY,
    warehouse_id         UUID         NOT NULL,
    source_location_id   UUID         NOT NULL,
    target_location_id   UUID         NOT NULL,
    sku_id               UUID         NOT NULL,
    lot_id               UUID,
    quantity             INTEGER      NOT NULL,
    reason_code          VARCHAR(40)  NOT NULL,
    reason_note          VARCHAR(500),
    actor_id             VARCHAR(100) NOT NULL,
    idempotency_key      VARCHAR(100),
    version              BIGINT       NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ  NOT NULL,
    created_by           VARCHAR(100) NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL,
    updated_by           VARCHAR(100) NOT NULL,
    CONSTRAINT ck_transfer_quantity_positive
        CHECK (quantity > 0),
    CONSTRAINT ck_transfer_distinct_locations
        CHECK (source_location_id <> target_location_id),
    CONSTRAINT ck_transfer_reason_code
        CHECK (reason_code IN ('TRANSFER_INTERNAL', 'REPLENISHMENT', 'CONSOLIDATION'))
);

CREATE INDEX idx_transfer_warehouse_created
    ON stock_transfer (warehouse_id, created_at DESC);

CREATE INDEX idx_transfer_source_location
    ON stock_transfer (source_location_id, created_at DESC);

CREATE INDEX idx_transfer_target_location
    ON stock_transfer (target_location_id, created_at DESC);
