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
-- Canonical schema = the runtime entity TmsRequestDedupeEntity (request_id /
-- sent_at / response_snapshot), identical to the V13 re-CREATE.
-- TASK-BE-333: an earlier draft here defined shipment_id/idempotency_key/
-- tms_status/requested_at, which conflicted with both the runtime entity and the
-- V13 `CREATE TABLE IF NOT EXISTS` + `sent_at` index → the Flyway chain failed
-- with "column sent_at does not exist" (latent — outbound-service was never
-- deployed nor CI-gated). Reconciled in place to the entity/V13 schema.
CREATE TABLE tms_request_dedupe (
    request_id        UUID                     PRIMARY KEY,
    sent_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    response_snapshot JSONB                    NOT NULL
);

CREATE INDEX idx_packing_unit_order_id ON packing_unit(order_id);
CREATE INDEX idx_shipment_order_id ON shipment(order_id);
