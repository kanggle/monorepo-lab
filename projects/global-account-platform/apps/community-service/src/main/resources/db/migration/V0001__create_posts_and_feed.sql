CREATE TABLE posts (
    id                  VARCHAR(36)   NOT NULL,
    author_account_id   VARCHAR(36)   NOT NULL,
    type                VARCHAR(20)   NOT NULL,
    visibility          VARCHAR(20)   NOT NULL DEFAULT 'PUBLIC',
    status              VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    title               VARCHAR(200)  NULL,
    body                TEXT          NULL,
    media_urls          JSON          NULL,
    published_at        DATETIME(6)   NULL,
    created_at          DATETIME(6)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,
    deleted_at          DATETIME(6)   NULL,
    version             INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_posts_author (author_account_id),
    INDEX idx_posts_status_published (status, published_at),
    INDEX idx_posts_type_visibility (type, visibility)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE feed_subscriptions (
    fan_account_id      VARCHAR(36)   NOT NULL,
    artist_account_id   VARCHAR(36)   NOT NULL,
    followed_at         DATETIME(6)   NOT NULL,
    PRIMARY KEY (fan_account_id, artist_account_id),
    INDEX idx_feed_fan (fan_account_id, followed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
