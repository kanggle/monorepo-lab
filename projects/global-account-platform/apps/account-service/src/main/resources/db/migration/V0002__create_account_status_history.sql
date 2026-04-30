CREATE TABLE account_status_history (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    account_id   VARCHAR(36)   NOT NULL,
    from_status  VARCHAR(20)   NOT NULL,
    to_status    VARCHAR(20)   NOT NULL,
    reason_code  VARCHAR(50)   NOT NULL,
    actor_type   VARCHAR(20)   NOT NULL,
    actor_id     VARCHAR(36)   NULL,
    details      JSON          NULL,
    occurred_at  DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_ash_account_id_occurred_at (account_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
