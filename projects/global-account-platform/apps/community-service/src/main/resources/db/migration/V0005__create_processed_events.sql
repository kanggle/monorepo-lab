-- processed_events table for libs/java-messaging inbox pattern
-- Hibernate schema-validation requires this table because ProcessedEventJpaEntity
-- is auto-scanned via OutboxJpaConfig (@EntityScan) whenever java-messaging is on
-- the classpath. Schema must match ProcessedEventJpaEntity exactly.

CREATE TABLE processed_events (
    event_id     VARCHAR(36)   NOT NULL,
    event_type   VARCHAR(100)  NOT NULL,
    processed_at DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
