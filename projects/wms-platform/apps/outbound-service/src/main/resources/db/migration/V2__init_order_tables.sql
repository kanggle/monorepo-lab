-- Outbound order tables. TASK-BE-034 creates the schema; domain code lands in
-- TASK-BE-035 (ReceiveOrderUseCase + Order aggregate).

CREATE TABLE outbound_order (
    id                   UUID         PRIMARY KEY,
    erp_order_number     VARCHAR(100) NOT NULL UNIQUE,
    warehouse_id         UUID         NOT NULL,
    partner_id           UUID         NOT NULL,
    status               VARCHAR(30)  NOT NULL DEFAULT 'RECEIVED',
    requested_ship_date  DATE,
    saga_id              UUID,
    idempotency_key      VARCHAR(255),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version              BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE outbound_order_line (
    id              UUID  PRIMARY KEY,
    order_id        UUID  NOT NULL REFERENCES outbound_order(id),
    sku_id          UUID  NOT NULL,
    lot_id          UUID,
    requested_qty   INT   NOT NULL,
    line_number     INT   NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_outbound_order_status ON outbound_order(status);
CREATE INDEX idx_outbound_order_line_order_id ON outbound_order_line(order_id);
