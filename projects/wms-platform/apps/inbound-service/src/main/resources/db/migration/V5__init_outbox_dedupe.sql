-- Transactional outbox + Kafka consumer-side eventId dedupe.
-- Authoritative reference: specs/services/inbound-service/domain-model.md §5, §6.
-- Rules: rules/traits/transactional.md — T3 (outbox), T8 (eventId dedupe).
--
-- Both tables are append-only. V7 enforces the W2 invariant by revoking
-- UPDATE/DELETE on these tables for the application role.

-- ---------------------------------------------------------------------------
-- InboundOutbox (T3)
--   The publisher process is wired in TASK-BE-030; this migration only creates
--   the table and indexes so use-cases can start writing rows once the first
--   mutation path is delivered.
-- ---------------------------------------------------------------------------
CREATE TABLE inbound_outbox (
    id              UUID         PRIMARY KEY,
    aggregate_type  VARCHAR(40)  NOT NULL,
    aggregate_id    UUID         NOT NULL,
    event_type      VARCHAR(60)  NOT NULL,
    event_version   VARCHAR(10)  NOT NULL DEFAULT 'v1',
    payload         JSONB        NOT NULL,
    partition_key   VARCHAR(60)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    published_at    TIMESTAMPTZ
);

-- Publisher polls pending rows in FIFO order.
CREATE INDEX idx_inbound_outbox_pending
    ON inbound_outbox (created_at)
    WHERE published_at IS NULL;

CREATE INDEX idx_inbound_outbox_aggregate
    ON inbound_outbox (aggregate_type, aggregate_id);

-- ---------------------------------------------------------------------------
-- InboundEventDedupe (T8)
--   eventId-keyed dedupe for all consumers. INSERT-or-skip pattern; a duplicate
--   eventId is detected by primary-key conflict and the use-case body is not
--   re-executed.
-- ---------------------------------------------------------------------------
CREATE TABLE inbound_event_dedupe (
    event_id      UUID         PRIMARY KEY,
    event_type    VARCHAR(60)  NOT NULL,
    processed_at  TIMESTAMPTZ  NOT NULL,
    outcome       VARCHAR(20)  NOT NULL,
    CONSTRAINT ck_inbound_dedupe_outcome
        CHECK (outcome IN ('APPLIED', 'IGNORED_DUPLICATE', 'FAILED'))
);

-- Retention sweeper: prune rows older than 30 days.
CREATE INDEX idx_inbound_dedupe_processed_at
    ON inbound_event_dedupe (processed_at);
