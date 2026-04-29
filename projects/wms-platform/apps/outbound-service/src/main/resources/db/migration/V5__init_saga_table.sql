-- OutboundSaga state aggregate. Schema only; saga-step consumer wiring lands in
-- TASK-BE-036.

CREATE TABLE outbound_saga (
    id                   UUID PRIMARY KEY,
    order_id             UUID NOT NULL UNIQUE,
    status               VARCHAR(30) NOT NULL DEFAULT 'REQUESTED',
    reservation_id       UUID,
    picking_request_id   UUID,
    shipment_id          UUID,
    failure_reason       VARCHAR(500),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version              BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_outbound_saga_status ON outbound_saga(status);
CREATE INDEX idx_outbound_saga_order_id ON outbound_saga(order_id);
