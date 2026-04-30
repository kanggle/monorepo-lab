-- TASK-BE-041b: account.locked 이벤트 수신 이력을 append-only로 보관.
-- audit-heavy A3: UPDATE/DELETE 금지. row-per-event 이력 테이블.
CREATE TABLE account_lock_history (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    event_id      VARCHAR(36)   NOT NULL,
    account_id    VARCHAR(36)   NOT NULL,
    reason        VARCHAR(255)  NOT NULL,
    locked_by     VARCHAR(36)   NOT NULL,
    source        VARCHAR(32)   NOT NULL,
    occurred_at   DATETIME(6)   NOT NULL,
    received_at   DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_account_lock_history_event_id (event_id),
    INDEX idx_account_lock_history_account_occurred (account_id, occurred_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
