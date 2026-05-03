-- fan-platform artist-service initial schema (PostgreSQL).
-- Multi-tenant: every domain table carries tenant_id; key indexes prefix
-- tenant_id per rules/domains/fan-platform.md F7.
--
-- Hexagonal architecture (per task spec § Architecture): tables here are
-- mapped by JPA entities under adapter/out/persistence/. The domain layer
-- under domain/{artist,group,fandom} stays framework-free.

-- ---------------------------------------------------------------------------
-- artists — aggregate root for artist profile (SOLO or GROUP_MEMBER)
-- ---------------------------------------------------------------------------
CREATE TABLE artists (
    id                  VARCHAR(36)   PRIMARY KEY,
    tenant_id           VARCHAR(64)   NOT NULL,
    artist_type         VARCHAR(20)   NOT NULL,
    status              VARCHAR(20)   NOT NULL,
    stage_name          VARCHAR(120)  NOT NULL,
    real_name           VARCHAR(120),
    debut_date          DATE,
    agency              VARCHAR(120),
    bio                 TEXT,
    profile_image_ref   VARCHAR(500),
    created_at          TIMESTAMPTZ   NOT NULL,
    updated_at          TIMESTAMPTZ   NOT NULL,
    published_at        TIMESTAMPTZ,
    archived_at         TIMESTAMPTZ,
    version             BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_artists_artist_type CHECK (artist_type IN ('SOLO', 'GROUP_MEMBER')),
    CONSTRAINT ck_artists_status      CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    -- F-edge case: stage_name UNIQUE per tenant (case-sensitive — caller normalizes)
    CONSTRAINT uq_artists_tenant_stage_name UNIQUE (tenant_id, stage_name)
);
-- Directory query: tenant_id + status + stage_name (for q-prefix searches)
CREATE INDEX idx_artists_tenant_status_stage_name
    ON artists (tenant_id, status, stage_name);
-- Type filter: tenant_id + artist_type + status
CREATE INDEX idx_artists_tenant_type_status
    ON artists (tenant_id, artist_type, status);

-- ---------------------------------------------------------------------------
-- artist_groups — group entity (e.g., a K-pop group with N members)
-- ---------------------------------------------------------------------------
CREATE TABLE artist_groups (
    id                  VARCHAR(36)   PRIMARY KEY,
    tenant_id           VARCHAR(64)   NOT NULL,
    name                VARCHAR(120)  NOT NULL,
    debut_date          DATE,
    agency              VARCHAR(120),
    profile_image_ref   VARCHAR(500),
    status              VARCHAR(20)   NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL,
    updated_at          TIMESTAMPTZ   NOT NULL,
    archived_at         TIMESTAMPTZ,
    version             BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_artist_groups_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    -- Group name is unique per tenant.
    CONSTRAINT uq_artist_groups_tenant_name UNIQUE (tenant_id, name)
);
CREATE INDEX idx_artist_groups_tenant_name
    ON artist_groups (tenant_id, name);

-- ---------------------------------------------------------------------------
-- group_memberships — N:M relation between artist_groups and artists.
-- Composite PK lets the same artist re-join the same group on a new joined_at.
-- ---------------------------------------------------------------------------
CREATE TABLE group_memberships (
    group_id            VARCHAR(36)   NOT NULL,
    artist_id           VARCHAR(36)   NOT NULL,
    tenant_id           VARCHAR(64)   NOT NULL,
    role                VARCHAR(20)   NOT NULL,
    joined_at           TIMESTAMPTZ   NOT NULL,
    left_at             TIMESTAMPTZ,
    PRIMARY KEY (group_id, artist_id, joined_at),
    CONSTRAINT ck_group_memberships_role
        CHECK (role IN ('LEADER', 'MEMBER', 'FORMER_MEMBER'))
);
-- Reverse-lookup ("which groups is this artist in"): tenant_id prefix per F7
-- (rules/domains/fan-platform.md) + (rules/traits/multi-tenant.md M2).
CREATE INDEX idx_group_memberships_artist
    ON group_memberships (tenant_id, artist_id);
CREATE INDEX idx_group_memberships_tenant_group
    ON group_memberships (tenant_id, group_id);

-- ---------------------------------------------------------------------------
-- fandoms — 1:1 with artists (a published artist may have one fandom).
-- ---------------------------------------------------------------------------
CREATE TABLE fandoms (
    artist_id           VARCHAR(36)   PRIMARY KEY,
    tenant_id           VARCHAR(64)   NOT NULL,
    fandom_name         VARCHAR(120)  NOT NULL,
    color_hex           VARCHAR(7),
    founded_at          DATE,
    slogan              VARCHAR(200),
    created_at          TIMESTAMPTZ   NOT NULL,
    updated_at          TIMESTAMPTZ   NOT NULL,
    version             BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_fandoms_color_hex CHECK (color_hex IS NULL OR color_hex ~ '^#[0-9A-Fa-f]{6}$')
);
CREATE INDEX idx_fandoms_tenant
    ON fandoms (tenant_id);

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
