-- TASK-BE-446: migrate shipping-service outbox v1 -> v2 (AbstractOutboxPublisher).
--
-- Adds the v2-shaped `shipping_outbox` table consumed by ShippingOutboxPublisher
-- (extends AbstractOutboxPublisher<ShippingOutboxEntity>). Columns match
-- libs/java-messaging OutboxRowEntity (UUID event_id PK, occurred_at, retries +
-- last_error). Mirrors the CI-validated master_outbox (TASK-BE-438) /
-- promotion_outbox (TASK-BE-444) / review_outbox (TASK-BE-445).
--
-- EntityScan / retained v1 tables (DO NOT DROP). libs/java-messaging
-- force-registers OutboxJpaEntity (table `outbox`) and ProcessedEventJpaEntity
-- (table `processed_events`, V4) in its EntityScan; the application also
-- @EntityScans `com.example.messaging` explicitly. With ddl-auto=validate both
-- tables MUST remain present. V9 ADDS `shipping_outbox` and leaves the v1
-- `outbox` (now unused) and `processed_events` (still the consumer-dedupe table
-- for OrderConfirmed / WmsShippingConfirmed / WmsOutboundCancelled) in place.
--
-- Cutover disposition. Any unpublished rows in the v1 `outbox` at deploy time are
-- no longer polled (the v2 poller reads `shipping_outbox`). They are deliberately
-- ABANDONED: in the demo / fed-e2e / CI usage this monorepo targets, the
-- shipping / fulfillment events are re-derivable. (TASK-BE-446 Edge Case
-- "Cutover" / Failure Scenario F1.)

CREATE TABLE shipping_outbox (
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
CREATE INDEX idx_shipping_outbox_pending
    ON shipping_outbox (occurred_at)
    WHERE published_at IS NULL;
