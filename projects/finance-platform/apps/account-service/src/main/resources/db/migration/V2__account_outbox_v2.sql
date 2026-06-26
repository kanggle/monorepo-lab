-- finance-platform account-service outbox v1 -> v2 (TASK-FIN-BE-045).
-- MySQL 8, InnoDB, utf8mb4 — parity with V1. Migrates account-service onto the
-- shared v2 AbstractOutboxPublisher (the OutboxRow path, ADR-MONO-004 § 5),
-- matching the in-platform ledger-service precedent (V3__create_ledger_outbox).
--
-- account_outbox — pending rows have published_at IS NULL; the relay
-- (AccountOutboxPublisher) forwards them to Kafka in created-order and stamps
-- published_at after the ACK (at-least-once; downstream dedupe on the envelope
-- eventId). id is the UUIDv7 event id stored as its 36-char canonical string —
-- matches AccountOutboxJpaEntity's UUID field mapped via @JdbcTypeCode(SqlTypes.CHAR).
-- payload is the full v1 envelope JSON (TEXT — the MySQL precedent, NOT jsonb);
-- the envelope wire shape is preserved exactly (7-field BaseEventPublisher shape).
CREATE TABLE account_outbox (
    id              CHAR(36)     NOT NULL,
    aggregate_type  VARCHAR(60)  NOT NULL,
    aggregate_id    VARCHAR(64)  NOT NULL,
    event_type      VARCHAR(80)  NOT NULL,
    event_version   VARCHAR(10)  NOT NULL,
    payload         TEXT         NOT NULL,
    partition_key   VARCHAR(64)  NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    published_at    DATETIME(6),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_account_outbox_unpublished ON account_outbox (published_at, created_at);

-- The v1 `outbox` (BIGINT/status) and `processed_events` tables from V1__init are
-- intentionally NOT dropped. account-service now excludes the libs
-- OutboxAutoConfiguration (+ OutboxMetricsAutoConfiguration), so the libs
-- OutboxJpaEntity/ProcessedEventJpaEntity are no longer EntityScanned and
-- hibernate.ddl-auto=validate no longer requires those tables. They remain as
-- present-but-unused stubs (droppable by a later cleanup task). Any unpublished
-- rows left in the v1 `outbox` at cutover are abandoned (low-volume,
-- re-derivable in demo/fed-e2e/CI — TASK-FIN-BE-045 Edge Case "cutover" / F1).
