CREATE TABLE outbox (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    aggregate_type VARCHAR(100)  NOT NULL,
    aggregate_id   VARCHAR(255)  NOT NULL,
    event_type     VARCHAR(100)  NOT NULL,
    payload        TEXT          NOT NULL,
    created_at     TIMESTAMP     NOT NULL,
    published_at   TIMESTAMP     NULL,
    status         VARCHAR(20)   NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_outbox_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
