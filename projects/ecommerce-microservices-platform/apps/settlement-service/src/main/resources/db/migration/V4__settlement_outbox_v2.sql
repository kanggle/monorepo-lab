-- TASK-BE-447: migrate settlement-service outbox v1 -> v2 (AbstractOutboxPublisher).
--
-- Adds the v2-shaped `settlement_outbox` table consumed by SettlementOutboxPublisher
-- (extends AbstractOutboxPublisher<SettlementOutboxEntity>). Columns match
-- libs/java-messaging OutboxRowEntity (UUID event_id PK, occurred_at, retries +
-- last_error). Mirrors the CI-validated master_outbox (TASK-BE-438) and the other
-- ecommerce v2 tables (promotion/review/shipping).
--
-- EntityScan / retained v1 tables (DO NOT DROP). The service @EntityScans
-- `com.example.messaging` and imports the lib OutboxAutoConfiguration, so the lib
-- OutboxJpaEntity (table `outbox`, created in V2) is registered; with
-- hibernate.ddl-auto=validate it MUST remain present. V4 ADDS `settlement_outbox`
-- and leaves the v1 `outbox` table (now unused) in place. The locally-owned
-- `processed_event` consumer-dedupe table (V1) is unrelated and untouched.
--
-- Cutover disposition. Any unpublished rows in the v1 `outbox` at deploy time are
-- no longer polled (the v2 poller reads `settlement_outbox`). They are deliberately
-- ABANDONED: settlement.period.closed.v1 has no live downstream consumer and the
-- close is re-derivable in the demo / CI usage this monorepo targets.
-- (TASK-BE-447 Edge Case "Cutover" / Failure Scenario F1.)

CREATE TABLE settlement_outbox (
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
CREATE INDEX idx_settlement_outbox_pending
    ON settlement_outbox (occurred_at)
    WHERE published_at IS NULL;
