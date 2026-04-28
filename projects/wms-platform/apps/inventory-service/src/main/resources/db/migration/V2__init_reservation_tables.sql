-- Reservation aggregate (root) and ReservationLine child rows.
-- Authoritative reference: specs/services/inventory-service/domain-model.md §3
--
-- Notes:
--   - picking_request_id is unique across all Reservations regardless of status,
--     enforcing per-picking-request idempotency at the DB layer alongside the
--     REST Idempotency-Key store.
--   - ReservationLine has no version of its own; the Reservation aggregate's
--     version covers the whole graph.
--   - status check constraint mirrors the state machine: RESERVED → CONFIRMED
--     or RESERVED → RELEASED, both terminal. Domain-layer transitions enforce
--     the rule; the SQL constraint exists only to catch out-of-band INSERTs.

CREATE TABLE reservation (
    id                  UUID         PRIMARY KEY,
    picking_request_id  UUID         NOT NULL,
    warehouse_id        UUID         NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    expires_at          TIMESTAMPTZ  NOT NULL,
    released_reason     VARCHAR(20),
    confirmed_at        TIMESTAMPTZ,
    released_at         TIMESTAMPTZ,
    version             BIGINT       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL,
    created_by          VARCHAR(100) NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    updated_by          VARCHAR(100) NOT NULL,
    CONSTRAINT uq_reservation_picking_request_id UNIQUE (picking_request_id),
    CONSTRAINT ck_reservation_status
        CHECK (status IN ('RESERVED', 'CONFIRMED', 'RELEASED')),
    CONSTRAINT ck_reservation_released_reason
        CHECK (released_reason IS NULL OR released_reason IN ('CANCELLED', 'EXPIRED', 'MANUAL'))
);

CREATE INDEX idx_reservation_status_expires_at
    ON reservation (status, expires_at)
    WHERE status = 'RESERVED';

CREATE INDEX idx_reservation_warehouse_status
    ON reservation (warehouse_id, status);

CREATE TABLE reservation_line (
    id              UUID         PRIMARY KEY,
    reservation_id  UUID         NOT NULL REFERENCES reservation (id) ON DELETE CASCADE,
    inventory_id    UUID         NOT NULL,
    location_id     UUID         NOT NULL,
    sku_id          UUID         NOT NULL,
    lot_id          UUID,
    quantity        INTEGER      NOT NULL,
    CONSTRAINT ck_reservation_line_quantity_positive
        CHECK (quantity > 0),
    CONSTRAINT uq_reservation_line_inventory
        UNIQUE (reservation_id, inventory_id)
);

CREATE INDEX idx_reservation_line_reservation
    ON reservation_line (reservation_id);

CREATE INDEX idx_reservation_line_inventory
    ON reservation_line (inventory_id);
