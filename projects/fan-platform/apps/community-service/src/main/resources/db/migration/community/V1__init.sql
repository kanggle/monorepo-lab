-- fan-platform community-service initial schema (PostgreSQL).
-- Multi-tenant: every table carries tenant_id; key indexes prefix tenant_id
-- per rules/traits/multi-tenant.md M2.

-- ---------------------------------------------------------------------------
-- posts — top-level aggregate
-- ---------------------------------------------------------------------------
CREATE TABLE posts (
    id                  VARCHAR(36)   PRIMARY KEY,
    tenant_id           VARCHAR(64)   NOT NULL,
    author_account_id   VARCHAR(36)   NOT NULL,
    post_type           VARCHAR(20)   NOT NULL,
    visibility          VARCHAR(20)   NOT NULL,
    status              VARCHAR(20)   NOT NULL,
    title               VARCHAR(200),
    body                TEXT,
    media_refs          JSONB,
    published_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ   NOT NULL,
    updated_at          TIMESTAMPTZ   NOT NULL,
    deleted_at          TIMESTAMPTZ,
    version             BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_posts_post_type   CHECK (post_type IN ('ARTIST_POST', 'FAN_POST')),
    CONSTRAINT ck_posts_visibility  CHECK (visibility IN ('PUBLIC', 'MEMBERS_ONLY', 'PREMIUM')),
    CONSTRAINT ck_posts_status      CHECK (status IN ('DRAFT', 'PUBLISHED', 'HIDDEN', 'DELETED'))
);
-- Feed query: tenant_id + status + published_at DESC
CREATE INDEX idx_posts_tenant_status_published
    ON posts (tenant_id, status, published_at DESC);
-- Author profile listing: tenant_id + author + published_at DESC
CREATE INDEX idx_posts_tenant_author
    ON posts (tenant_id, author_account_id, published_at DESC);

-- ---------------------------------------------------------------------------
-- post_status_history — append-only audit trail
-- ---------------------------------------------------------------------------
CREATE TABLE post_status_history (
    id                  BIGSERIAL     PRIMARY KEY,
    post_id             VARCHAR(36)   NOT NULL,
    tenant_id           VARCHAR(64)   NOT NULL,
    from_status         VARCHAR(20)   NOT NULL,
    to_status           VARCHAR(20)   NOT NULL,
    actor_type          VARCHAR(20)   NOT NULL,
    actor_account_id    VARCHAR(36),
    reason              VARCHAR(200),
    occurred_at         TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_psh_actor_type CHECK (actor_type IN ('AUTHOR', 'OPERATOR', 'SYSTEM'))
);
CREATE INDEX idx_psh_tenant_post_occurred
    ON post_status_history (tenant_id, post_id, occurred_at DESC);

-- ---------------------------------------------------------------------------
-- comments
-- ---------------------------------------------------------------------------
CREATE TABLE comments (
    id                  VARCHAR(36)   PRIMARY KEY,
    tenant_id           VARCHAR(64)   NOT NULL,
    post_id             VARCHAR(36)   NOT NULL,
    author_account_id   VARCHAR(36)   NOT NULL,
    body                TEXT          NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL,
    deleted_at          TIMESTAMPTZ,
    version             BIGINT        NOT NULL DEFAULT 0
);
CREATE INDEX idx_comments_tenant_post_created
    ON comments (tenant_id, post_id, created_at);

-- ---------------------------------------------------------------------------
-- reactions — composite PK (post_id, reactor_account_id), upserted in place
-- ---------------------------------------------------------------------------
CREATE TABLE reactions (
    post_id             VARCHAR(36)   NOT NULL,
    reactor_account_id  VARCHAR(36)   NOT NULL,
    tenant_id           VARCHAR(64)   NOT NULL,
    reaction_type       VARCHAR(20)   NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL,
    updated_at          TIMESTAMPTZ   NOT NULL,
    PRIMARY KEY (post_id, reactor_account_id),
    CONSTRAINT ck_reactions_type CHECK (reaction_type IN ('LIKE', 'LOVE', 'FIRE', 'SAD'))
);
CREATE INDEX idx_reactions_tenant_post
    ON reactions (tenant_id, post_id);

-- ---------------------------------------------------------------------------
-- follows — asymmetric fan↔artist relationship
-- ---------------------------------------------------------------------------
CREATE TABLE follows (
    fan_account_id      VARCHAR(36)   NOT NULL,
    artist_account_id   VARCHAR(36)   NOT NULL,
    tenant_id           VARCHAR(64)   NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL,
    PRIMARY KEY (fan_account_id, artist_account_id)
);
CREATE INDEX idx_follows_tenant_artist
    ON follows (tenant_id, artist_account_id);
CREATE INDEX idx_follows_tenant_fan
    ON follows (tenant_id, fan_account_id);

-- ---------------------------------------------------------------------------
-- outbox — schema matches libs/java-messaging OutboxJpaEntity exactly.
-- Keep field names + types in sync with libs/java-messaging's
-- ProcessedEventJpaEntity / OutboxJpaEntity (PostgreSQL flavour).
-- ---------------------------------------------------------------------------
CREATE TABLE outbox (
    id              BIGSERIAL    PRIMARY KEY,
    aggregate_type  VARCHAR(100) NOT NULL,
    aggregate_id    VARCHAR(255) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         TEXT         NOT NULL,
    created_at      TIMESTAMP    NOT NULL,
    published_at    TIMESTAMP,
    status          VARCHAR(20)  NOT NULL,
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);
CREATE INDEX idx_outbox_status_created_at
    ON outbox (status, created_at);

-- processed_events — required by Hibernate schema-validation because
-- ProcessedEventJpaEntity is auto-scanned via OutboxJpaConfig (@EntityScan).
CREATE TABLE processed_events (
    event_id        VARCHAR(100) PRIMARY KEY,
    event_type      VARCHAR(100) NOT NULL,
    processed_at    TIMESTAMP    NOT NULL
);
