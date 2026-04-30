CREATE TABLE login_history (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    event_id           VARCHAR(36)   NOT NULL,
    account_id         VARCHAR(36)   NULL,
    outcome            VARCHAR(30)   NOT NULL,
    ip_masked          VARCHAR(45)   NULL,
    user_agent_family  VARCHAR(100)  NULL,
    device_fingerprint VARCHAR(128)  NULL,
    geo_country        VARCHAR(10)   NULL,
    occurred_at        DATETIME(6)   NOT NULL,
    created_at         DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE INDEX idx_login_history_event_id (event_id),
    INDEX idx_login_history_account_id (account_id),
    INDEX idx_login_history_occurred_at (occurred_at),
    INDEX idx_login_history_account_outcome (account_id, outcome)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
