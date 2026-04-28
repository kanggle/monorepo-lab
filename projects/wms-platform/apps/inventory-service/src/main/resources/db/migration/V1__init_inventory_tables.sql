-- Inventory core tables, append-only Movement ledger, transactional outbox,
-- and consumer-side EventDedupe.
--
-- Authoritative reference: specs/services/inventory-service/domain-model.md
--   §1 Inventory, §2 InventoryMovement, §6 InventoryOutbox, §7 EventDedupe
-- Rules: rules/domains/wms.md (W1, W2), rules/traits/transactional.md (T3, T8)
--
-- Invariants enforced at schema level:
--   - Non-negative bucket quantities (ck_inventory_buckets_nonneg)
--   - Bucket / movement_type / status enum constraints
--   - qty_after = qty_before + delta on movement (ck_movement_after_consistent)
--   - (location_id, sku_id, COALESCE(lot_id, '00...')) uniqueness — partial-unique
--     index using a deterministic surrogate UUID for NULL lot_id (NULL is not
--     equal to NULL in SQL, so the natural key collapses without a sentinel)
--   - inventory_movement append-only at the SQL role level (V5 grant revocation)
--   - inventory_event_dedupe append-only by primary key (event_id is unique)

-- ---------------------------------------------------------------------------
-- 1. Inventory aggregate root
-- ---------------------------------------------------------------------------
CREATE TABLE inventory (
    id                 UUID         PRIMARY KEY,
    warehouse_id       UUID         NOT NULL,
    location_id        UUID         NOT NULL,
    sku_id             UUID         NOT NULL,
    lot_id             UUID,
    available_qty      INTEGER      NOT NULL DEFAULT 0,
    reserved_qty       INTEGER      NOT NULL DEFAULT 0,
    damaged_qty        INTEGER      NOT NULL DEFAULT 0,
    last_movement_at   TIMESTAMPTZ  NOT NULL,
    version            BIGINT       NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ  NOT NULL,
    created_by         VARCHAR(100) NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    updated_by         VARCHAR(100) NOT NULL,
    CONSTRAINT ck_inventory_buckets_nonneg
        CHECK (available_qty >= 0 AND reserved_qty >= 0 AND damaged_qty >= 0)
);

-- Logical-key uniqueness with NULL-safe lot. Two partial unique indexes cover
-- the LOT-tracked and non-LOT-tracked cases without needing a NULL sentinel.
CREATE UNIQUE INDEX uq_inventory_loc_sku_lot
    ON inventory (location_id, sku_id, lot_id)
    WHERE lot_id IS NOT NULL;

CREATE UNIQUE INDEX uq_inventory_loc_sku_no_lot
    ON inventory (location_id, sku_id)
    WHERE lot_id IS NULL;

CREATE INDEX idx_inventory_warehouse_sku
    ON inventory (warehouse_id, sku_id);

CREATE INDEX idx_inventory_sku
    ON inventory (sku_id);

-- ---------------------------------------------------------------------------
-- 2. InventoryMovement append-only ledger (W2)
-- ---------------------------------------------------------------------------
CREATE TABLE inventory_movement (
    id                UUID         PRIMARY KEY,
    inventory_id      UUID         NOT NULL REFERENCES inventory (id),
    movement_type     VARCHAR(30)  NOT NULL,
    bucket            VARCHAR(20)  NOT NULL,
    delta             INTEGER      NOT NULL,
    qty_before        INTEGER      NOT NULL,
    qty_after         INTEGER      NOT NULL,
    reason_code       VARCHAR(40)  NOT NULL,
    reason_note       VARCHAR(500),
    reservation_id    UUID,
    transfer_id       UUID,
    adjustment_id     UUID,
    source_event_id   UUID,
    actor_id          VARCHAR(100) NOT NULL,
    occurred_at       TIMESTAMPTZ  NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_movement_bucket
        CHECK (bucket IN ('AVAILABLE', 'RESERVED', 'DAMAGED')),
    CONSTRAINT ck_movement_type
        CHECK (movement_type IN (
            'RECEIVE', 'RESERVE', 'RELEASE', 'CONFIRM', 'ADJUSTMENT',
            'TRANSFER_OUT', 'TRANSFER_IN', 'DAMAGE_MARK', 'DAMAGE_WRITE_OFF'
        )),
    CONSTRAINT ck_movement_after_nonneg
        CHECK (qty_before >= 0 AND qty_after >= 0),
    CONSTRAINT ck_movement_after_consistent
        CHECK (qty_after = qty_before + delta)
);

-- Hot-path query: movement history for one Inventory row, newest first.
CREATE INDEX idx_movement_inventory_occurred
    ON inventory_movement (inventory_id, occurred_at DESC);

-- Cross-row movement queries with `occurredAfter` filter.
CREATE INDEX idx_movement_occurred
    ON inventory_movement (occurred_at DESC);

CREATE INDEX idx_movement_reservation
    ON inventory_movement (reservation_id)
    WHERE reservation_id IS NOT NULL;

CREATE INDEX idx_movement_transfer
    ON inventory_movement (transfer_id)
    WHERE transfer_id IS NOT NULL;

CREATE INDEX idx_movement_adjustment
    ON inventory_movement (adjustment_id)
    WHERE adjustment_id IS NOT NULL;

CREATE INDEX idx_movement_source_event
    ON inventory_movement (source_event_id)
    WHERE source_event_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 3. InventoryOutbox (T3)
--   The publisher process is wired in TASK-BE-022; this migration only creates
--   the table and indexes so use-cases can start writing rows once the first
--   mutation path is delivered.
-- ---------------------------------------------------------------------------
CREATE TABLE inventory_outbox (
    id              UUID         PRIMARY KEY,
    aggregate_type  VARCHAR(40)  NOT NULL,
    aggregate_id    UUID         NOT NULL,
    event_type      VARCHAR(60)  NOT NULL,
    event_version   VARCHAR(10)  NOT NULL DEFAULT 'v1',
    payload         JSONB        NOT NULL,
    partition_key   VARCHAR(60)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    published_at    TIMESTAMPTZ
);

-- Publisher polls pending rows in FIFO order.
CREATE INDEX idx_inventory_outbox_pending
    ON inventory_outbox (created_at)
    WHERE published_at IS NULL;

CREATE INDEX idx_inventory_outbox_aggregate
    ON inventory_outbox (aggregate_type, aggregate_id);

-- ---------------------------------------------------------------------------
-- 4. EventDedupe (T8)
--   eventId-keyed dedupe for all consumers. INSERT-or-skip pattern; a duplicate
--   eventId is detected by primary-key conflict and the use-case body is not
--   re-executed.
-- ---------------------------------------------------------------------------
CREATE TABLE inventory_event_dedupe (
    event_id      UUID         PRIMARY KEY,
    event_type    VARCHAR(60)  NOT NULL,
    processed_at  TIMESTAMPTZ  NOT NULL,
    outcome       VARCHAR(20)  NOT NULL,
    CONSTRAINT ck_dedupe_outcome
        CHECK (outcome IN ('APPLIED', 'IGNORED_DUPLICATE', 'FAILED'))
);

-- Retention sweeper: prune rows older than 30 days.
CREATE INDEX idx_dedupe_processed_at
    ON inventory_event_dedupe (processed_at);
