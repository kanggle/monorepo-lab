-- notification-service core tables.
--
-- Authoritative reference:
--   specs/services/notification-service/architecture.md § Persistence
--   specs/services/notification-service/domain-model.md § Persistence Layout
-- Rules: rules/traits/integration-heavy.md (I1-I5),
--        rules/traits/transactional.md (T1, T3, T4, T8)
--
-- Schema-level invariants:
--   - delivery_idempotency_key UNIQUE blocks double-row creation under
--     concurrent routing (architecture.md § Idempotency)
--   - status, severity, dedupe outcome enums are SQL-checked
--   - JSONB columns map to JPA entities annotated with
--     @JdbcTypeCode(SqlTypes.JSON) — see TASK-SCM-INT-001b root cause #2
--     and TASK-SCM-BE-005 regression-guard learning. JsonbColumnRegressionGuardTest
--     enforces this at build time.

-- ---------------------------------------------------------------------------
-- 1. notification_routing_rule
--    Per-event-type routing decision. Seeded by V2__seed_routing_rules.sql.
-- ---------------------------------------------------------------------------
CREATE TABLE notification_routing_rule (
    id                    UUID            PRIMARY KEY,
    event_type            VARCHAR(120)    NOT NULL,
    matcher_json          JSONB           NOT NULL,
    channel_targets_json  JSONB           NOT NULL,
    severity              VARCHAR(16)     NOT NULL,
    enabled               BOOLEAN         NOT NULL DEFAULT true,
    created_at            TIMESTAMPTZ     NOT NULL,
    updated_at            TIMESTAMPTZ     NOT NULL,
    CONSTRAINT ck_routing_rule_severity
        CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL'))
);

-- One enabled rule per eventType in v1 (T6 — no ambiguity). Surfaces only on
-- bad manual DB edit; ROUTING_AMBIGUOUS is raised when multiple match.
CREATE UNIQUE INDEX uq_routing_rule_event_enabled
    ON notification_routing_rule (event_type)
    WHERE enabled = true;

-- ---------------------------------------------------------------------------
-- 2. notification_delivery
--    One logical delivery (one channel × one event). State machine:
--      PENDING → SUCCEEDED | FAILED  (terminal).
-- ---------------------------------------------------------------------------
CREATE TABLE notification_delivery (
    id                          UUID            PRIMARY KEY,
    event_id                    UUID            NOT NULL,
    source_topic                VARCHAR(120)    NOT NULL,
    channel_id                  VARCHAR(120)    NOT NULL,
    recipient                   VARCHAR(255)    NOT NULL,
    delivery_idempotency_key    VARCHAR(64)     NOT NULL UNIQUE,
    payload_snapshot            JSONB           NOT NULL,
    status                      VARCHAR(16)     NOT NULL,
    attempt_count               INT             NOT NULL DEFAULT 0,
    scheduled_retry_at          TIMESTAMPTZ     NULL,
    last_error                  VARCHAR(500)    NULL,
    version                     INT             NOT NULL DEFAULT 0,
    created_at                  TIMESTAMPTZ     NOT NULL,
    updated_at                  TIMESTAMPTZ     NOT NULL,
    CONSTRAINT ck_delivery_status
        CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_delivery_attempt_nonneg
        CHECK (attempt_count >= 0)
);

-- Hot-path query: retry scheduler picks PENDING rows due for retry. Partial
-- index keeps it fast even as terminal rows accumulate.
CREATE INDEX idx_delivery_status_retry
    ON notification_delivery (status, scheduled_retry_at)
    WHERE status = 'PENDING';

CREATE INDEX idx_delivery_event_id
    ON notification_delivery (event_id);

-- ---------------------------------------------------------------------------
-- 3. notification_event_dedupe (T8)
--    eventId-keyed dedupe across all subscribed topics. Insert-only.
-- ---------------------------------------------------------------------------
CREATE TABLE notification_event_dedupe (
    event_id      UUID            PRIMARY KEY,
    source_topic  VARCHAR(120)    NOT NULL,
    processed_at  TIMESTAMPTZ     NOT NULL,
    outcome       VARCHAR(16)     NOT NULL,
    CONSTRAINT ck_dedupe_outcome
        CHECK (outcome IN ('QUEUED', 'FILTERED', 'NO_RULE', 'ERROR'))
);

-- 30-day retention sweeper hot-path (cleanup job is v2 scope).
CREATE INDEX idx_dedupe_processed_at
    ON notification_event_dedupe (processed_at);

-- ---------------------------------------------------------------------------
-- 4. notification_outbox (T3)
--    Standard transactional outbox — service-local (NOT libs/java-messaging
--    base) because the v1 schema mandates JSONB payload + partition_key.
--    Mirrors inventory_outbox shape.
-- ---------------------------------------------------------------------------
CREATE TABLE notification_outbox (
    id              UUID            PRIMARY KEY,
    aggregate_type  VARCHAR(64)     NOT NULL,
    aggregate_id    VARCHAR(64)     NOT NULL,
    event_type      VARCHAR(120)    NOT NULL,
    event_version   VARCHAR(10)     NOT NULL DEFAULT 'v1',
    payload         JSONB           NOT NULL,
    partition_key   VARCHAR(120)    NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL,
    published_at    TIMESTAMPTZ     NULL,
    attempt_count   INT             NOT NULL DEFAULT 0
);

CREATE INDEX idx_notification_outbox_pending
    ON notification_outbox (created_at)
    WHERE published_at IS NULL;

CREATE INDEX idx_notification_outbox_aggregate
    ON notification_outbox (aggregate_type, aggregate_id);
