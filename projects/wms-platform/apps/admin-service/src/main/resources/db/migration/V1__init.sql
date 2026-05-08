-- admin-service core tables (BE-045 write-side + outbox + dedupe).
--
-- Authoritative reference:
--   specs/services/admin-service/architecture.md § Persistence
--   specs/services/admin-service/domain-model.md § 1-4, §14-15
--   specs/services/admin-service/idempotency.md § 2.2
-- Rules: rules/traits/transactional.md (T1, T3, T4, T5, T8)
--
-- Schema-level invariants:
--   - admin_user.email UNIQUE (case-insensitive — stored lowercased; partial
--     unique index enforces it without relying on app-side validation).
--   - admin_user.user_code UNIQUE (immutable after creation by app rule).
--   - admin_role.role_code UNIQUE (immutable).
--   - admin_role.is_builtin marks the four seeded WMS_* roles so the app
--     can reject deactivation/deletion (ROLE_BUILTIN_IMMUTABLE).
--   - admin_user_role_assignment partial UNIQUE on (user_id, role_id,
--     warehouse_id) WHERE status='ACTIVE' — null warehouse_id treated as
--     a singleton via COALESCE to a fixed sentinel UUID.
--   - admin_setting composite PK on (key, COALESCE(warehouse_id, sentinel)).
--   - JSONB columns on admin_role.permissions_json, admin_setting.value_json /
--     schema_json, admin_outbox.payload — JPA entities MUST carry
--     @JdbcTypeCode(SqlTypes.JSON). JsonbColumnRegressionGuardTest enforces
--     this at build time (TASK-SCM-INT-001b root cause #2 + TASK-SCM-BE-005).
--
-- Note on admin_event_dedupe:
--   The table is created here (V1) so consumer wiring in BE-046 can flip on
--   without a follow-up migration. BE-045 itself does not write to this
--   table.

-- ---------------------------------------------------------------------------
-- 1. admin_user
-- ---------------------------------------------------------------------------
CREATE TABLE admin_user (
    id                      UUID            PRIMARY KEY,
    user_code               VARCHAR(40)     NOT NULL UNIQUE,
    email                   VARCHAR(200)    NOT NULL,
    name                    VARCHAR(200)    NOT NULL,
    phone                   VARCHAR(30)     NULL,
    status                  VARCHAR(16)     NOT NULL,
    default_warehouse_id    UUID            NULL,
    version                 BIGINT          NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ     NOT NULL,
    created_by              VARCHAR(120)    NULL,
    updated_at              TIMESTAMPTZ     NOT NULL,
    updated_by              VARCHAR(120)    NULL,
    CONSTRAINT ck_admin_user_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

-- Case-insensitive unique email (we lowercase before persisting; the index
-- still uses LOWER() to defend against accidental direct DB inserts).
CREATE UNIQUE INDEX uq_admin_user_email_ci
    ON admin_user (LOWER(email));

CREATE INDEX idx_admin_user_status
    ON admin_user (status);

-- ---------------------------------------------------------------------------
-- 2. admin_role
-- ---------------------------------------------------------------------------
CREATE TABLE admin_role (
    id                  UUID            PRIMARY KEY,
    role_code           VARCHAR(40)     NOT NULL UNIQUE,
    name                VARCHAR(100)    NOT NULL,
    description         VARCHAR(500)    NULL,
    permissions_json    JSONB           NOT NULL,
    status              VARCHAR(16)     NOT NULL,
    is_builtin          BOOLEAN         NOT NULL DEFAULT FALSE,
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ     NOT NULL,
    created_by          VARCHAR(120)    NULL,
    updated_at          TIMESTAMPTZ     NOT NULL,
    updated_by          VARCHAR(120)    NULL,
    CONSTRAINT ck_admin_role_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_admin_role_status
    ON admin_role (status);

-- ---------------------------------------------------------------------------
-- 3. admin_user_role_assignment
-- ---------------------------------------------------------------------------
CREATE TABLE admin_user_role_assignment (
    id              UUID            PRIMARY KEY,
    user_id         UUID            NOT NULL,
    role_id         UUID            NOT NULL,
    warehouse_id    UUID            NULL,
    granted_at      TIMESTAMPTZ     NOT NULL,
    granted_by      VARCHAR(120)    NOT NULL,
    revoked_at      TIMESTAMPTZ     NULL,
    revoked_by      VARCHAR(120)    NULL,
    status          VARCHAR(16)     NOT NULL,
    version         BIGINT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL,
    updated_at      TIMESTAMPTZ     NOT NULL,
    CONSTRAINT ck_admin_assignment_status
        CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT fk_admin_assignment_user
        FOREIGN KEY (user_id) REFERENCES admin_user (id),
    CONSTRAINT fk_admin_assignment_role
        FOREIGN KEY (role_id) REFERENCES admin_role (id)
);

-- Unique active assignment per (user, role, warehouse). null warehouse_id
-- collapses to a fixed sentinel so it participates in the unique guard.
CREATE UNIQUE INDEX uq_admin_assignment_active
    ON admin_user_role_assignment (
        user_id,
        role_id,
        COALESCE(warehouse_id, '00000000-0000-0000-0000-000000000000'::uuid)
    )
    WHERE status = 'ACTIVE';

CREATE INDEX idx_admin_assignment_user
    ON admin_user_role_assignment (user_id);

CREATE INDEX idx_admin_assignment_role
    ON admin_user_role_assignment (role_id);

-- ---------------------------------------------------------------------------
-- 4. admin_setting
--    Composite PK on (key, warehouse_id). For GLOBAL-scope keys we store the
--    sentinel UUID 00000000-... in warehouse_id so the PK can be NOT NULL on
--    both columns and JPA composite key handling stays simple. The CHECK
--    constraint pins the sentinel-vs-real-UUID semantics to scope.
-- ---------------------------------------------------------------------------
CREATE TABLE admin_setting (
    key             VARCHAR(100)    NOT NULL,
    warehouse_id    UUID            NOT NULL,
    scope           VARCHAR(16)     NOT NULL,
    value_json      JSONB           NOT NULL,
    schema_json     JSONB           NOT NULL,
    description     VARCHAR(500)    NULL,
    version         BIGINT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL,
    created_by      VARCHAR(120)    NULL,
    updated_at      TIMESTAMPTZ     NOT NULL,
    updated_by      VARCHAR(120)    NULL,
    PRIMARY KEY (key, warehouse_id),
    CONSTRAINT ck_admin_setting_scope
        CHECK (scope IN ('GLOBAL', 'WAREHOUSE')),
    CONSTRAINT ck_admin_setting_warehouse_required
        CHECK (
            (scope = 'GLOBAL' AND warehouse_id = '00000000-0000-0000-0000-000000000000'::uuid) OR
            (scope = 'WAREHOUSE' AND warehouse_id <> '00000000-0000-0000-0000-000000000000'::uuid)
        )
);

CREATE INDEX idx_admin_setting_scope
    ON admin_setting (scope);

-- ---------------------------------------------------------------------------
-- 5. admin_outbox (T3)
--    Service-local (NOT libs/java-messaging base) because the v1 schema
--    mandates JSONB payload + partition_key. Same decision as
--    notification-service (TASK-BE-043).
-- ---------------------------------------------------------------------------
CREATE TABLE admin_outbox (
    id              UUID            PRIMARY KEY,
    aggregate_type  VARCHAR(40)     NOT NULL,
    aggregate_id    VARCHAR(120)    NOT NULL,
    event_type      VARCHAR(60)     NOT NULL,
    event_version   VARCHAR(10)     NOT NULL DEFAULT 'v1',
    payload         JSONB           NOT NULL,
    partition_key   VARCHAR(120)    NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL,
    published_at    TIMESTAMPTZ     NULL,
    attempt_count   INT             NOT NULL DEFAULT 0
);

CREATE INDEX idx_admin_outbox_pending
    ON admin_outbox (created_at)
    WHERE published_at IS NULL;

CREATE INDEX idx_admin_outbox_aggregate
    ON admin_outbox (aggregate_type, aggregate_id);

-- ---------------------------------------------------------------------------
-- 6. admin_event_dedupe (T8)
--    Created in V1 even though BE-045 has no consumer — BE-046 will populate.
-- ---------------------------------------------------------------------------
CREATE TABLE admin_event_dedupe (
    event_id        UUID            PRIMARY KEY,
    event_type      VARCHAR(60)     NOT NULL,
    processed_at    TIMESTAMPTZ     NOT NULL DEFAULT now(),
    outcome         VARCHAR(30)     NOT NULL,
    CONSTRAINT ck_admin_dedupe_outcome
        CHECK (outcome IN ('APPLIED', 'IGNORED_DUPLICATE',
                           'IGNORED_DUPLICATE_LATE', 'FAILED'))
);

CREATE INDEX idx_admin_event_dedupe_processed_at
    ON admin_event_dedupe (processed_at);
