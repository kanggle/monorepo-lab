CREATE TABLE social_identities (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    account_id          VARCHAR(36)  NOT NULL,
    provider            VARCHAR(20)  NOT NULL,
    provider_user_id    VARCHAR(255) NOT NULL,
    provider_email      VARCHAR(255) NULL,
    connected_at        DATETIME(6)  NOT NULL,
    last_used_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE INDEX uk_social_provider_user (provider, provider_user_id),
    INDEX idx_social_account_id (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
