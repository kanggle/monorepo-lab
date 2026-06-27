-- iam-platform security-service outbox v1 -> v2 (TASK-BE-453).
-- MySQL 8, InnoDB, utf8mb4 — parity with V1. Migrates security-service onto the
-- shared v2 AbstractOutboxPublisher (the OutboxRow path, ADR-MONO-004 § 5),
-- mirroring the in-worktree auth-service migration (TASK-BE-450) + the finance
-- account-service MySQL precedent (TASK-FIN-BE-045) + erp approval-service
-- (TASK-ERP-BE-025).
--
-- security_outbox — pending rows have published_at IS NULL; the relay
-- (SecurityOutboxPublisher) forwards them to Kafka in created-order and stamps
-- published_at after the ACK (at-least-once; downstream dedupe on the envelope
-- eventId). id is the UUIDv7 event id stored as its 36-char canonical string —
-- matches SecurityOutboxJpaEntity's UUID field mapped via @JdbcTypeCode(SqlTypes.CHAR).
-- payload is the full v1 envelope JSON (TEXT — the MySQL precedent, NOT jsonb);
-- the envelope wire shape is preserved exactly (7-field BaseEventPublisher.writeEvent
-- shape: {eventId, eventType, source="security-service", occurredAt, schemaVersion=1,
-- partitionKey, payload}).
-- NB: no event_version column (the iam v1 BaseEventPublisher path never carried one).
-- event_type VARCHAR(80) fits the longest security event ("security.suspicious.detected"=29);
-- aggregate_id VARCHAR(64) fits the accountId partition keys.
CREATE TABLE security_outbox (
    id              CHAR(36)     NOT NULL,
    aggregate_type  VARCHAR(60)  NOT NULL,
    aggregate_id    VARCHAR(64)  NOT NULL,
    event_type      VARCHAR(80)  NOT NULL,
    payload         TEXT         NOT NULL,
    partition_key   VARCHAR(64)  NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    published_at    DATETIME(6),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_security_outbox_unpublished ON security_outbox (published_at, created_at);

-- KEEP-auto-config stance (NOT the finance account exclude stance). The v1
-- `outbox_events` (BIGINT/status, V0004 + V0005 rename) and `processed_events`
-- (V0003) tables are intentionally NOT dropped: security-service RETAINS the libs
-- OutboxAutoConfiguration (it is NOT excluded), so the libs OutboxJpaEntity /
-- ProcessedEventJpaEntity remain EntityScanned and hibernate.ddl-auto=validate
-- still requires both tables present. They remain as present-but-unused stubs
-- (droppable by a later cleanup task once the lib stops force-scanning the v1
-- entity). The v2 write adapter no longer uses the lib OutboxWriter and the v2
-- relay no longer drives the v1 `outbox_events`. Any unpublished rows left in the
-- v1 `outbox_events` at cutover are abandoned (low-volume, re-derivable in
-- demo/fed-e2e/CI — TASK-BE-453 Edge Case "cutover" / F1).
