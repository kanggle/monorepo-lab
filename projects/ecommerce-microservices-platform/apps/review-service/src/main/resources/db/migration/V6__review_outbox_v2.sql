-- TASK-BE-445: migrate review-service outbox v1 -> v2 (AbstractOutboxPublisher).
--
-- Adds the v2-shaped `review_outbox` table consumed by ReviewOutboxPublisher
-- (extends AbstractOutboxPublisher<ReviewOutboxEntity>). Columns match
-- libs/java-messaging OutboxRowEntity — the @MappedSuperclass that
-- ReviewOutboxEntity extends (UUID event_id PK, occurred_at as the domain event
-- timestamp, retries + last_error for the v2 backoff/failure tracking). Mirrors
-- the CI-validated master_outbox table (TASK-BE-438) / promotion_outbox
-- (TASK-BE-444).
--
-- EntityScan / retained v1 tables (DO NOT DROP). libs/java-messaging
-- force-registers OutboxJpaEntity (table `outbox`) and ProcessedEventJpaEntity
-- (table `processed_events`, created in V4) in its EntityScan via OutboxJpaConfig,
-- which the service still imports through the (retained) OutboxAutoConfiguration.
-- With hibernate.ddl-auto=validate, both tables MUST remain present or the
-- context fails to boot. V6 therefore ADDS `review_outbox` and leaves the v1
-- `outbox` (now unused) and `processed_events` in place.
--
-- Cutover disposition. Any unpublished rows still sitting in the v1 `outbox`
-- at deploy time are NOT migrated and are no longer polled (the v2 poller reads
-- `review_outbox`). They are deliberately ABANDONED: review's ReviewCreated /
-- ReviewUpdated / ReviewDeleted events have no live ecommerce consumer and are
-- re-derivable in the demo / fed-e2e / CI usage this monorepo targets.
-- (See TASK-BE-445 Edge Case "Cutover" / Failure Scenario F1.)

CREATE TABLE review_outbox (
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

-- Pending poll: `WHERE published_at IS NULL ORDER BY occurred_at ASC`. A partial
-- index keeps only the unpublished tail indexed (published rows are never
-- re-scanned), and orders it by occurrence for the FIFO drain.
CREATE INDEX idx_review_outbox_pending
    ON review_outbox (occurred_at)
    WHERE published_at IS NULL;
