-- logistics-service initial schema (PostgreSQL, schema scm_logistics).
-- ADR-MONO-053 Phase 1: carrier dispatch of confirmed shipments.
-- All tables carry tenant_id (echoed from the wms seam; "scm" or null for B2B).
-- TASK-SCM-BE-042.

-- ---------------------------------------------------------------------------
-- dispatch — the aggregate. Status machine PENDING → DISPATCHED /
-- PENDING → DISPATCH_FAILED → DISPATCHED (ADR-053 §D2, S1).
-- shipment_id is UNIQUE — a shipment dispatches exactly once (business dedup,
-- architecture.md § Idempotency layer 2).
-- ---------------------------------------------------------------------------
CREATE TABLE dispatch (
    id              UUID            PRIMARY KEY,
    shipment_id     UUID            NOT NULL,
    shipment_no     VARCHAR(100)    NOT NULL,
    order_id        UUID,
    order_no        VARCHAR(100),
    tenant_id       VARCHAR(64),
    carrier_code    VARCHAR(64),
    tracking_no     VARCHAR(200),
    status          VARCHAR(20)     NOT NULL,
    failure_reason  VARCHAR(1000),
    vendor          VARCHAR(32),
    version         INT             NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL,
    updated_at      TIMESTAMPTZ     NOT NULL,
    CONSTRAINT uq_dispatch_shipment_id UNIQUE (shipment_id),
    CONSTRAINT ck_dispatch_status
        CHECK (status IN ('PENDING', 'DISPATCHED', 'DISPATCH_FAILED'))
);

-- Operator filtering (inspect surface).
CREATE INDEX idx_dispatch_tenant_status ON dispatch (tenant_id, status);

-- ---------------------------------------------------------------------------
-- dispatch_request_dedupe — vendor idempotency ground-truth (I4, ADR-052 §2.7).
-- request_id = shipment_id (the stable Idempotency-Key toward the vendor). A
-- repeat send reads response_snapshot and short-circuits with no network call.
-- Relocated verbatim from the wms interim so dedupe semantics are byte-preserved.
-- ---------------------------------------------------------------------------
CREATE TABLE dispatch_request_dedupe (
    request_id          UUID            PRIMARY KEY,
    vendor              VARCHAR(32)     NOT NULL,
    sent_at             TIMESTAMPTZ     NOT NULL,
    response_snapshot   TEXT            NOT NULL
);

-- ---------------------------------------------------------------------------
-- processed_events — consumer idempotency for the seam consumer (T8).
-- SCAFFOLD: the ShippingConfirmedConsumer that writes this table is wired in
-- TASK-SCM-BE-044. The table lands now so BE-044 adds no migration.
-- ---------------------------------------------------------------------------
CREATE TABLE processed_events (
    event_id        UUID            PRIMARY KEY,
    tenant_id       VARCHAR(64),
    processed_at    TIMESTAMPTZ     NOT NULL,
    source_topic    VARCHAR(200)    NOT NULL
);
CREATE INDEX idx_processed_events_processed_at ON processed_events (processed_at);
