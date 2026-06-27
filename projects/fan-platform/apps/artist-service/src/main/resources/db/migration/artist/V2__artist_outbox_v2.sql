-- TASK-FAN-BE-022: migrate artist-service outbox v1 -> v2 (AbstractOutboxPublisher).
--
-- Adds the v2-shaped `artist_outbox` table consumed by ArtistOutboxPublisher
-- (extends AbstractOutboxPublisher<ArtistOutboxJpaEntity>). Columns match
-- libs/java-messaging OutboxRowEntity — the @MappedSuperclass that
-- ArtistOutboxJpaEntity extends (UUID event_id PK, occurred_at as the domain
-- event timestamp, retries + last_error for the v2 backoff/failure tracking).
-- Mirrors the CI-validated Postgres v2 reference (scm procurement_outbox,
-- TASK-SCM-BE-032; wms master_outbox, TASK-BE-438).
--
-- EntityScan / retained v1 tables (DO NOT DROP). libs/java-messaging
-- force-registers OutboxJpaEntity (table `outbox`) and ProcessedEventJpaEntity
-- (table `processed_events`) in its EntityScan via OutboxJpaConfig, which the
-- service still imports through the (retained) OutboxAutoConfiguration. With
-- hibernate.ddl-auto=validate, both tables MUST remain present or the context
-- fails to boot (V1__init.sql comment already notes processed_events is required
-- by the lib EntityScan). V2 therefore ADDS `artist_outbox` and leaves the
-- v1 `outbox` (now unused — no scheduler drives the v1 publisher anymore) and
-- `processed_events` (consumer-dedupe table) in place. A later cleanup task can
-- drop the v1 `outbox` once the lib stops force-scanning the v1 entity.
--
-- Cutover disposition. Any unpublished rows still sitting in the v1 `outbox`
-- at deploy time are NOT migrated and are no longer polled (the v2 poller reads
-- `artist_outbox`). They are deliberately ABANDONED: artist events are
-- re-derivable in the demo / fed-e2e / CI usage this monorepo targets.
-- (See TASK-FAN-BE-022 Edge Case "Cutover" / Failure Scenario F1.)

CREATE TABLE artist_outbox (
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
CREATE INDEX idx_artist_outbox_pending
    ON artist_outbox (occurred_at)
    WHERE published_at IS NULL;
