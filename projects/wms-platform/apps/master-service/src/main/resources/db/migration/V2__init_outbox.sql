-- Transactional outbox and idempotent-consumer dedupe tables.
-- Schemas match libs/java-messaging entities (OutboxJpaEntity,
-- ProcessedEventJpaEntity). master-service is a producer only in v1;
-- processed_events is still created because java-messaging registers both
-- entities in its EntityScan, and hibernate.ddl-auto=validate would fail
-- if either table were absent.

CREATE TABLE outbox (
    id              BIGSERIAL    PRIMARY KEY,
    aggregate_type  VARCHAR(100) NOT NULL,
    aggregate_id    VARCHAR(100) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         TEXT         NOT NULL,
    created_at      TIMESTAMP    NOT NULL,
    published_at    TIMESTAMP,
    status          VARCHAR(20)  NOT NULL,
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

-- Publisher polls pending rows in FIFO order.
CREATE INDEX idx_outbox_status_created_at
    ON outbox (status, created_at);

CREATE TABLE processed_events (
    event_id      VARCHAR(100) PRIMARY KEY,
    event_type    VARCHAR(100) NOT NULL,
    processed_at  TIMESTAMP    NOT NULL
);
