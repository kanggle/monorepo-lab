-- TASK-BE-448: migrate order-service outbox v1 -> v2 (AbstractOutboxPublisher).
--
-- Adds the v2-shaped `order_outbox` table consumed by OrderOutboxPublisher
-- (extends AbstractOutboxPublisher<OrderOutboxEntity>). Columns match
-- libs/java-messaging OutboxRowEntity (UUID event_id PK, occurred_at, retries +
-- last_error). Mirrors the CI-validated master_outbox (TASK-BE-438) and the other
-- ecommerce v2 tables.
--
-- order-service is saga-critical: its events (order.order.placed/confirmed/cancelled,
-- order.alert.saga.recovery.exhausted) drive payment / product / settlement /
-- shipping / notification / promotion. Wire is preserved exactly — same topics,
-- same payload JSON, same Kafka key (aggregateId = orderId). The eventId/eventType
-- Kafka headers are additive (v1 sent none); ecommerce consumers parse the payload
-- JSON, so the headers do not alter existing consumption.
--
-- EntityScan / retained v1 tables (DO NOT DROP). The service @EntityScans
-- `com.example.messaging` and imports the lib OutboxAutoConfiguration, so the lib
-- OutboxJpaEntity (table `outbox`, V5) and ProcessedEventJpaEntity (table
-- `processed_events`, V6) are registered; with ddl-auto=validate both MUST remain
-- present. V10 ADDS `order_outbox` and leaves the v1 `outbox` (now unused) and
-- `processed_events` (still the consumer-dedupe table) in place.
--
-- Cutover disposition. Any unpublished rows in the v1 `outbox` at deploy time are
-- no longer polled (the v2 poller reads `order_outbox`). They are deliberately
-- ABANDONED: in the demo / fed-e2e / CI usage this monorepo targets the saga is
-- re-driven from a clean state. (TASK-BE-448 Edge Case "Cutover" / F1.)

CREATE TABLE order_outbox (
    event_id       UUID         PRIMARY KEY,
    event_type     VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(60)  NOT NULL,
    aggregate_id   VARCHAR(60)  NOT NULL,
    partition_key  VARCHAR(60),
    payload        TEXT         NOT NULL,
    occurred_at    TIMESTAMP    NOT NULL,
    published_at   TIMESTAMP,
    retries        INT          NOT NULL DEFAULT 0,
    last_error     TEXT
);

-- Pending poll: `WHERE published_at IS NULL ORDER BY occurred_at ASC`. Partial
-- index keeps only the unpublished tail indexed, ordered by occurrence (FIFO drain).
CREATE INDEX idx_order_outbox_pending
    ON order_outbox (occurred_at)
    WHERE published_at IS NULL;
