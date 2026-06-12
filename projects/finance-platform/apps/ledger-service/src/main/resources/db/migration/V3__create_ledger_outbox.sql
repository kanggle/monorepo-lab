-- finance-platform ledger-service GL/AP-feed outbox schema (3rd increment,
-- TASK-FIN-BE-009). MySQL 8, InnoDB, utf8mb4 — parity with V1/V2.
-- Per-service transactional outbox (the OutboxRow path — ADR-MONO-004): the
-- ledger now emits finance.ledger.{entry.posted,period.closed}.v1 as a one-way
-- GL/AP feed. The libs OutboxAutoConfiguration stays excluded (its
-- ProcessedEventJpaEntity would collide with this service's own processed_events
-- consumer-dedupe table), so this table is owned by LedgerOutboxJpaEntity.

-- ---------------------------------------------------------------------------
-- ledger_outbox — pending rows have published_at IS NULL; the relay
-- (LedgerOutboxPublisher) forwards them to Kafka created-order and stamps
-- published_at after the ACK (at-least-once; downstream dedupe on the envelope
-- eventId). id is the UUIDv7 event id stored as its 36-char canonical string —
-- matches the entity's UUID field mapped via @JdbcTypeCode(SqlTypes.CHAR). The
-- payload is the full canonical envelope JSON (TEXT — the account-service outbox
-- MySQL precedent, NOT Postgres jsonb).
-- ---------------------------------------------------------------------------
CREATE TABLE ledger_outbox (
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
CREATE INDEX idx_ledger_outbox_unpublished ON ledger_outbox (published_at, created_at);
