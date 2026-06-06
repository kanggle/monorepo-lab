CREATE TABLE accounts (
    id          VARCHAR(36)   NOT NULL,
    email       VARCHAR(255)  NOT NULL,
    status      VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    deleted_at  DATETIME(6)   NULL,
    version     INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE INDEX idx_accounts_email (email),
    INDEX idx_accounts_status (status),
    INDEX idx_accounts_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE profiles (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    account_id   VARCHAR(36)   NOT NULL,
    display_name VARCHAR(100)  NULL,
    phone_number VARCHAR(20)   NULL,
    birth_date   DATE          NULL,
    locale       VARCHAR(10)   NOT NULL DEFAULT 'ko-KR',
    timezone     VARCHAR(50)   NOT NULL DEFAULT 'Asia/Seoul',
    preferences  JSON          NULL,
    updated_at   DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE INDEX idx_profiles_account_id (account_id),
    CONSTRAINT fk_profiles_account_id FOREIGN KEY (account_id) REFERENCES accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
