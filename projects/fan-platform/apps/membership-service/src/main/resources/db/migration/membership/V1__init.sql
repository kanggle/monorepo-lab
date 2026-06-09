-- fan-platform membership-service initial schema (PostgreSQL).
-- Multi-tenant: every table carries tenant_id; key indexes prefix tenant_id
-- per rules/traits/multi-tenant.md M2.

-- ---------------------------------------------------------------------------
-- memberships — single windowed-subscription aggregate.
--
-- CHECK allow-list (feedback_spring_boot_diagnostic_patterns §16): the tier /
-- status CHECK constraints fix the value set at the DB level. Adding a future
-- tier/status value (e.g. a stored EXPIRED) requires a V2 migration to extend
-- the allow-list. A Docker-free :check slice will NOT catch a violation, so the
-- Testcontainers IT is the authoritative gate (§14).
--
-- valid_from / valid_to are TIMESTAMPTZ (micros precision). The subscribe write
-- truncates ClockPort.now() to micros (§15) so the in-memory response equals a
-- DB re-read (Postgres TIMESTAMPTZ stores microseconds; nanosecond Instant.now()
-- would round-trip differently).
-- ---------------------------------------------------------------------------
CREATE TABLE memberships (
    id              VARCHAR(36)   PRIMARY KEY,
    tenant_id       VARCHAR(64)   NOT NULL,
    account_id      VARCHAR(36)   NOT NULL,
    tier            VARCHAR(20)   NOT NULL,
    status          VARCHAR(20)   NOT NULL,
    valid_from      TIMESTAMPTZ   NOT NULL,
    valid_to        TIMESTAMPTZ   NOT NULL,
    plan_months     INT           NOT NULL,
    payment_ref     VARCHAR(80)   NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL,
    canceled_at     TIMESTAMPTZ,
    version         BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_membership_tier        CHECK (tier IN ('MEMBERS_ONLY', 'PREMIUM')),
    CONSTRAINT ck_membership_status      CHECK (status IN ('ACTIVE', 'CANCELED')),
    CONSTRAINT ck_membership_plan_months CHECK (plan_months >= 1)
);
-- Access-check point lookup + the caller's membership listing.
CREATE INDEX idx_memberships_tenant_account_status
    ON memberships (tenant_id, account_id, status);
-- Newest-window-first listing.
CREATE INDEX idx_memberships_tenant_account_validto
    ON memberships (tenant_id, account_id, valid_to DESC);

-- ---------------------------------------------------------------------------
-- idempotency_keys — subscribe idempotency. Composite PK
-- (tenant_id, account_id, idempotency_key). A replay with the same key + same
-- fingerprint returns the stored membership_id (idempotent); same key +
-- different fingerprint → 409 IDEMPOTENCY_KEY_CONFLICT.
-- ---------------------------------------------------------------------------
CREATE TABLE idempotency_keys (
    tenant_id            VARCHAR(64)  NOT NULL,
    account_id           VARCHAR(36)  NOT NULL,
    idempotency_key      VARCHAR(80)  NOT NULL,
    request_fingerprint  VARCHAR(128) NOT NULL,
    membership_id        VARCHAR(36)  NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (tenant_id, account_id, idempotency_key)
);

-- ---------------------------------------------------------------------------
-- outbox — schema matches libs/java-messaging OutboxJpaEntity exactly.
-- Keep field names + types in sync with libs/java-messaging's
-- OutboxJpaEntity (PostgreSQL flavour).
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
