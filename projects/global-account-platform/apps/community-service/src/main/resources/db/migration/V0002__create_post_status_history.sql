CREATE TABLE post_status_history (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    post_id      VARCHAR(36)   NOT NULL,
    from_status  VARCHAR(20)   NOT NULL,
    to_status    VARCHAR(20)   NOT NULL,
    actor_type   VARCHAR(20)   NOT NULL,
    actor_id     VARCHAR(36)   NULL,
    reason       VARCHAR(100)  NULL,
    occurred_at  DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_post_status_history_post (post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Append-only: forbid UPDATE and DELETE on post_status_history
CREATE TRIGGER trg_post_status_history_no_update
BEFORE UPDATE ON post_status_history
FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'post_status_history is append-only';

CREATE TRIGGER trg_post_status_history_no_delete
BEFORE DELETE ON post_status_history
FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'post_status_history is append-only';
