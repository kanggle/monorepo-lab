CREATE TABLE comments (
    id                  VARCHAR(36)   NOT NULL,
    post_id             VARCHAR(36)   NOT NULL,
    author_account_id   VARCHAR(36)   NOT NULL,
    body                TEXT          NOT NULL,
    created_at          DATETIME(6)   NOT NULL,
    deleted_at          DATETIME(6)   NULL,
    PRIMARY KEY (id),
    INDEX idx_comments_post (post_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE reactions (
    post_id     VARCHAR(36)   NOT NULL,
    account_id  VARCHAR(36)   NOT NULL,
    emoji_code  VARCHAR(20)   NOT NULL,
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    PRIMARY KEY (post_id, account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
