CREATE TABLE auth_audit_log (
    id          UUID        PRIMARY KEY,
    user_id     UUID,
    email       VARCHAR(255) NOT NULL,
    event_type  VARCHAR(50)  NOT NULL,
    ip_address  VARCHAR(45),
    user_agent  VARCHAR(500),
    result      VARCHAR(20)  NOT NULL,
    failure_reason VARCHAR(255),
    created_at  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_audit_log_user_id         ON auth_audit_log (user_id);
CREATE INDEX idx_audit_log_event_type      ON auth_audit_log (event_type);
CREATE INDEX idx_audit_log_created_at      ON auth_audit_log (created_at);
CREATE INDEX idx_audit_log_user_event_type ON auth_audit_log (user_id, event_type);
