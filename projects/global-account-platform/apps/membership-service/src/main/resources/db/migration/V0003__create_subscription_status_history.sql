CREATE TABLE subscription_status_history (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    subscription_id  VARCHAR(36)   NOT NULL,
    account_id       VARCHAR(36)   NOT NULL,
    from_status      VARCHAR(20)   NOT NULL,
    to_status        VARCHAR(20)   NOT NULL,
    reason           VARCHAR(50)   NOT NULL,
    actor_type       VARCHAR(20)   NOT NULL,
    occurred_at      DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_ssh_subscription (subscription_id),
    INDEX idx_ssh_account (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TRIGGER trg_subscription_status_history_no_update
BEFORE UPDATE ON subscription_status_history FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'UPDATE on subscription_status_history is forbidden (append-only)';

CREATE TRIGGER trg_subscription_status_history_no_delete
BEFORE DELETE ON subscription_status_history FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'DELETE on subscription_status_history is forbidden (append-only)';
