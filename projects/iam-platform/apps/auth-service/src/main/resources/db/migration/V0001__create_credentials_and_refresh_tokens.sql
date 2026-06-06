CREATE TABLE credentials (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    account_id      VARCHAR(36)   NOT NULL,
    credential_hash VARCHAR(255)  NOT NULL,
    hash_algorithm  VARCHAR(30)   NOT NULL DEFAULT 'argon2id',
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,
    version         INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE INDEX idx_credentials_account_id (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE refresh_tokens (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    jti                VARCHAR(36)  NOT NULL,
    account_id         VARCHAR(36)  NOT NULL,
    issued_at          DATETIME(6)  NOT NULL,
    expires_at         DATETIME(6)  NOT NULL,
    rotated_from       VARCHAR(36)  NULL,
    revoked            BOOLEAN      NOT NULL DEFAULT FALSE,
    device_fingerprint VARCHAR(128) NULL,
    PRIMARY KEY (id),
    UNIQUE INDEX idx_rt_jti (jti),
    INDEX idx_rt_account_id (account_id),
    INDEX idx_rt_rotated_from (rotated_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
