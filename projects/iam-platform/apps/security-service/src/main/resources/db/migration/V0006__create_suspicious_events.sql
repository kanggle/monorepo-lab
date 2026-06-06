CREATE TABLE suspicious_events (
    id                   VARCHAR(36)   NOT NULL,
    account_id           VARCHAR(36)   NOT NULL,
    rule_code            VARCHAR(30)   NOT NULL,
    risk_score           INT           NOT NULL,
    action_taken         VARCHAR(20)   NOT NULL,
    evidence             JSON          NULL,
    trigger_event_id     VARCHAR(36)   NULL,
    detected_at          DATETIME(6)   NOT NULL,
    lock_request_result  VARCHAR(30)   NULL,
    created_at           DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_suspicious_account_detected (account_id, detected_at),
    INDEX idx_suspicious_rule_detected (rule_code, detected_at),
    INDEX idx_suspicious_action (action_taken)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
