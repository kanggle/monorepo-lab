-- Picking workflow tables. Schema only; domain code lands in TASK-BE-036.

CREATE TABLE picking_request (
    id            UUID  PRIMARY KEY,
    order_id      UUID  NOT NULL REFERENCES outbound_order(id),
    warehouse_id  UUID  NOT NULL,
    status        VARCHAR(30) NOT NULL DEFAULT 'REQUESTED',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE picking_request_line (
    id                   UUID PRIMARY KEY,
    picking_request_id   UUID NOT NULL REFERENCES picking_request(id),
    order_line_id        UUID NOT NULL,
    sku_id               UUID NOT NULL,
    lot_id               UUID,
    location_id          UUID NOT NULL,
    requested_qty        INT  NOT NULL,
    picked_qty           INT  NOT NULL DEFAULT 0
);

CREATE TABLE picking_confirmation (
    id                   UUID PRIMARY KEY,
    picking_request_id   UUID NOT NULL REFERENCES picking_request(id),
    confirmed_by         VARCHAR(100),
    confirmed_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE picking_confirmation_line (
    id                       UUID PRIMARY KEY,
    picking_confirmation_id  UUID NOT NULL REFERENCES picking_confirmation(id),
    picking_line_id          UUID NOT NULL,
    picked_qty               INT  NOT NULL
);

CREATE INDEX idx_picking_request_order_id ON picking_request(order_id);
CREATE INDEX idx_picking_request_status ON picking_request(status);
