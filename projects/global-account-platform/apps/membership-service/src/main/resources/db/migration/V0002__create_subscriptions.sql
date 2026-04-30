CREATE TABLE subscriptions (
    id             VARCHAR(36)   NOT NULL,
    account_id     VARCHAR(36)   NOT NULL,
    plan_level     VARCHAR(20)   NOT NULL,
    status         VARCHAR(20)   NOT NULL,
    started_at     DATETIME(6)   NOT NULL,
    expires_at     DATETIME(6)   NULL,
    cancelled_at   DATETIME(6)   NULL,
    created_at     DATETIME(6)   NOT NULL,
    version        INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_subscriptions_account_status (account_id, status),
    INDEX idx_subscriptions_expires (expires_at, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
