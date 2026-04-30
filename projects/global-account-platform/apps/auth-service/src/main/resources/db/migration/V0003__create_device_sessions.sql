-- TASK-BE-023: device session registry + refresh_tokens.device_id column.
-- Spec: specs/services/auth-service/device-session.md
-- Cascade behaviour (revoking child refresh_tokens when a device_session is revoked) is
-- enforced by the application layer (RevokeSessionUseCase / EnforceConcurrentLimitUseCase),
-- NOT by DB-level ON DELETE CASCADE. Service-layer ownership is required so the cascade
-- runs inside the same transaction as the outbox event write.

CREATE TABLE device_sessions (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    device_id          VARCHAR(36)  NOT NULL,
    account_id         VARCHAR(36)  NOT NULL,
    device_fingerprint VARCHAR(128) NOT NULL,
    user_agent         VARCHAR(512) NULL,
    ip_last            VARCHAR(45)  NULL,
    geo_last           VARCHAR(2)   NULL,
    issued_at          DATETIME(6)  NOT NULL,
    last_seen_at       DATETIME(6)  NOT NULL,
    revoked_at         DATETIME(6)  NULL,
    revoke_reason      VARCHAR(40)  NULL,
    PRIMARY KEY (id),
    UNIQUE INDEX idx_device_sessions_device_id (device_id),
    INDEX idx_device_sessions_account_active (account_id, revoked_at),
    INDEX idx_device_sessions_last_seen (account_id, last_seen_at),
    UNIQUE INDEX uk_device_sessions_account_fp_first_seen (account_id, device_fingerprint, issued_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- D5: refresh_tokens.device_id (nullable during the migration window — older rows may
-- predate device_session integration). device_fingerprint column is intentionally retained
-- and only deprecated; physical DROP is deferred to TASK-BE-024.
ALTER TABLE refresh_tokens
    ADD COLUMN device_id VARCHAR(36) NULL AFTER device_fingerprint,
    ADD INDEX idx_rt_device_id (device_id);
