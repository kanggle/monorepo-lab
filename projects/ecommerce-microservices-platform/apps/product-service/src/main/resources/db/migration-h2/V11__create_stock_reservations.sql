-- TASK-BE-428 — payment-driven stock-reservation + backorder saga (H2 standalone variant).
-- Mirrors db/migration/V16 with H2/PostgreSQL-mode types (TIMESTAMP, CURRENT_TIMESTAMP).

-- ---- 1. reservation aggregate ----------------------------------------------
CREATE TABLE stock_reservations (
    id               UUID         NOT NULL,
    order_id         VARCHAR(64)  NOT NULL,
    tenant_id        VARCHAR(64)  NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    payment_received BOOLEAN      NOT NULL DEFAULT false,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_stock_reservations PRIMARY KEY (id),
    CONSTRAINT uq_stock_reservations_order_id UNIQUE (order_id)
);
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
CREATE INDEX idx_stock_reservation_lines_variant ON stock_reservation_lines (variant_id);
CREATE INDEX idx_stock_reservation_lines_reservation ON stock_reservation_lines (reservation_id);

-- ---- 3. idempotent-consumer dedupe -----------------------------------------
CREATE TABLE reservation_processed_events (
    event_id     UUID         NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_reservation_processed_events PRIMARY KEY (event_id)
);
