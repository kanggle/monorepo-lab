-- TASK-BE-449: migrate payment-service outbox v1 -> v2 (AbstractOutboxPublisher).
--
-- Adds the v2-shaped `payment_outbox` table consumed by PaymentOutboxPublisher
-- (extends AbstractOutboxPublisher<PaymentOutboxEntity>). Columns match
-- libs/java-messaging OutboxRowEntity (UUID event_id PK, occurred_at, retries +
-- last_error). Mirrors the CI-validated master_outbox (TASK-BE-438) and the other
-- ecommerce v2 tables.
--
-- payment-service is MONEY-CRITICAL: its events (payment.payment.completed/refunded,
-- payment.alert.refund.stranded/unresolved) drive order saga confirmation, settlement
-- accrual/reversal, product, and notification. Wire is preserved exactly — same
-- topics, same payload JSON, same Kafka key (aggregateId = paymentId). The
-- eventId/eventType Kafka headers are additive (v1 sent none); consumers parse the
-- payload JSON, so the headers do not alter existing consumption.
--
-- EntityScan / retained v1 tables (DO NOT DROP). The service imports the lib
-- OutboxAutoConfiguration (OutboxJpaConfig EntityScans the lib OutboxJpaEntity →
-- table `outbox`, V3; and ProcessedEventJpaEntity → table `processed_events`, V4);
-- with ddl-auto=validate both MUST remain present. V8 ADDS `payment_outbox` and
-- leaves the v1 `outbox` (now unused) and `processed_events` (still the
-- consumer-dedupe table) in place.
--
-- Cutover disposition. Any unpublished rows in the v1 `outbox` at deploy time are
-- no longer polled (the v2 poller reads `payment_outbox`). They are deliberately
-- ABANDONED in the demo / fed-e2e / CI usage this monorepo targets. (TASK-BE-449
-- Edge Case "Cutover" / F1.)

CREATE TABLE payment_outbox (
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
CREATE INDEX idx_payment_outbox_pending
    ON payment_outbox (occurred_at)
    WHERE published_at IS NULL;
