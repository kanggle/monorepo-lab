-- TASK-BE-428 — payment-driven stock-reservation + backorder saga.
--
-- The NEW reservation bounded context: product-service consumes order/payment events and
-- reserves (all-or-nothing) stock for paid orders, holding shorts for FIFO restock retry.
--
-- 1. stock_reservations:        one row per order_id (unique), the reservation state machine
--                               (NEW|RESERVED|BACKORDERED|RELEASED) + payment-received flag.
-- 2. stock_reservation_lines:   child lines (variant_id, product_id, quantity), FK to reservation.
-- 3. reservation_processed_events: idempotent-consumer dedupe on the inbound envelope event_id
--                               (mirrors wms_processed_event; product-service publishes directly,
--                               not via the outbox).

-- ---- 1. reservation aggregate ----------------------------------------------
CREATE TABLE stock_reservations (
    id               UUID         NOT NULL,
    order_id         VARCHAR(64)  NOT NULL,
    tenant_id        VARCHAR(64)  NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    payment_received BOOLEAN      NOT NULL DEFAULT false,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_stock_reservations PRIMARY KEY (id),
    CONSTRAINT uq_stock_reservations_order_id UNIQUE (order_id)
);
-- FIFO restock retry orders BACKORDERED reservations by created_at; the status filter leads.
CREATE INDEX idx_stock_reservations_status_created ON stock_reservations (status, created_at);

-- ---- 2. reservation lines --------------------------------------------------
CREATE TABLE stock_reservation_lines (
    id             UUID   NOT NULL,
    reservation_id UUID   NOT NULL,
    variant_id     UUID   NOT NULL,
    product_id     UUID   NOT NULL,
    quantity       INT    NOT NULL,
    version        BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_stock_reservation_lines PRIMARY KEY (id),
    CONSTRAINT fk_stock_reservation_lines_reservation
        FOREIGN KEY (reservation_id) REFERENCES stock_reservations (id) ON DELETE CASCADE,
    CONSTRAINT chk_stock_reservation_lines_quantity CHECK (quantity > 0)
);
-- Restock retry resolves BACKORDERED reservations holding a given variant.
CREATE INDEX idx_stock_reservation_lines_variant ON stock_reservation_lines (variant_id);
CREATE INDEX idx_stock_reservation_lines_reservation ON stock_reservation_lines (reservation_id);

-- ---- 3. idempotent-consumer dedupe -----------------------------------------
CREATE TABLE reservation_processed_events (
    event_id     UUID         NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_reservation_processed_events PRIMARY KEY (event_id)
);
