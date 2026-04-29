-- Transactional outbox + Kafka consumer-side eventId dedupe.
-- Authoritative reference: specs/services/outbound-service/domain-model.md §7, §8.
-- Rules: rules/traits/transactional.md — T3 (outbox), T8 (eventId dedupe).
--
-- Both tables are append-only. V8 enforces the W2 invariant by revoking
-- UPDATE/DELETE on these tables for the application role.

CREATE TABLE outbound_outbox (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id    UUID NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ,
    retry_count     INT NOT NULL DEFAULT 0
);

CREATE TABLE outbound_event_dedupe (
    event_id      UUID PRIMARY KEY,
    event_type    VARCHAR(100) NOT NULL,
    processed_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_outbound_outbox_status ON outbound_outbox(status) WHERE status = 'PENDING';
