-- Packing + shipping + TMS dedupe. Schema only; domain code lands in TASK-BE-037.

CREATE TABLE packing_unit (
    id              UUID PRIMARY KEY,
    order_id        UUID NOT NULL REFERENCES outbound_order(id),
    tracking_number VARCHAR(100),
    status          VARCHAR(30) NOT NULL DEFAULT 'PACKING',
    packed_at       TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE packing_unit_line (
    id              UUID PRIMARY KEY,
    packing_unit_id UUID NOT NULL REFERENCES packing_unit(id),
    order_line_id   UUID NOT NULL,
    sku_id          UUID NOT NULL,
    lot_id          UUID,
    packed_qty      INT  NOT NULL
);

CREATE TABLE shipment (
    id              UUID PRIMARY KEY,
    order_id        UUID NOT NULL REFERENCES outbound_order(id),
    carrier         VARCHAR(100),
    tracking_number VARCHAR(200),
    status          VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    shipped_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- TMS request idempotency dedupe (per integration-heavy I4).
CREATE TABLE tms_request_dedupe (
    shipment_id      UUID PRIMARY KEY,
    idempotency_key  VARCHAR(255) NOT NULL,
    tms_status       VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    requested_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_packing_unit_order_id ON packing_unit(order_id);
CREATE INDEX idx_shipment_order_id ON shipment(order_id);
