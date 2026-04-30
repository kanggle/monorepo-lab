CREATE TABLE admin_actions (
    id                 VARCHAR(36)   NOT NULL,
    action_code        VARCHAR(50)   NOT NULL,
    actor_id           VARCHAR(100)  NOT NULL,
    actor_role         VARCHAR(30)   NOT NULL,
    target_type        VARCHAR(30)   NOT NULL,
    target_id          VARCHAR(100)  NOT NULL,
    reason             VARCHAR(1000) NOT NULL,
    ticket_id          VARCHAR(100)  NULL,
    idempotency_key    VARCHAR(100)  NOT NULL,
    outcome            VARCHAR(20)   NOT NULL,
    downstream_detail  TEXT          NULL,
    started_at         DATETIME(6)   NOT NULL,
    completed_at       DATETIME(6)   NULL,
    PRIMARY KEY (id),
    INDEX idx_admin_actions_actor (actor_id, started_at),
    INDEX idx_admin_actions_target (target_type, target_id, started_at),
    INDEX idx_admin_actions_action (action_code, started_at),
    UNIQUE INDEX idx_admin_actions_idemp (actor_id, action_code, idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Append-only: block UPDATE and DELETE (A3)
CREATE TRIGGER trg_admin_actions_no_update
BEFORE UPDATE ON admin_actions FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'UPDATE on admin_actions is forbidden (append-only)';

CREATE TRIGGER trg_admin_actions_no_delete
BEFORE DELETE ON admin_actions FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'DELETE on admin_actions is forbidden (append-only)';
