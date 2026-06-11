-- fan-platform notification-service initial schema (PostgreSQL 16).
-- event-consumer terminal service: consumes membership lifecycle events, records
-- one in-app notification per fresh event (idempotently), and serves a
-- recipient-scoped inbox. NO outbox table — this service publishes no events
-- (terminal consumer; OutboxAutoConfiguration is excluded in
-- NotificationServiceApplication).
--
-- Multi-tenant: every table carries tenant_id; the inbox index prefixes
-- tenant_id (rules/traits/multi-tenant.md M2).

-- ---------------------------------------------------------------------------
-- notifications — one in-app notification per fan account, derived from one
-- membership lifecycle event. 'status' (UNREAD/READ) + 'read_at' is the single
-- mutable state.
--
-- CHECK allow-list (feedback_spring_boot_diagnostic_patterns §16): the type /
-- status CHECK constraints fix the value set at the DB level. Adding a future
-- type (e.g. EXPIRY_REMINDER) or status requires a V2 migration to extend the
-- allow-list. A Docker-free :check slice will NOT catch a violation, so the
-- Testcontainers IT is the authoritative gate (§14).
--
-- created_at / read_at are TIMESTAMPTZ (micros precision). The consume write
-- truncates ClockPort.now() to micros (§15) so an in-memory value equals a DB
-- re-read (Postgres TIMESTAMPTZ stores microseconds; nanosecond Instant.now()
-- would round-trip differently).
--
-- source_event_id is the consumed envelope eventId — UNIQUE so a duplicate that
-- slips past the processed_events guard still cannot create a second row
-- (architecture.md § Idempotency, secondary natural guard).
-- ---------------------------------------------------------------------------
CREATE TABLE notifications (
    id                 VARCHAR(36)   PRIMARY KEY,
    tenant_id          VARCHAR(64)   NOT NULL,
    account_id         VARCHAR(36)   NOT NULL,
    type               VARCHAR(32)   NOT NULL,
    title              VARCHAR(256)  NOT NULL,
    body               VARCHAR(2000) NOT NULL,
    status             VARCHAR(16)   NOT NULL,
    source_event_id    VARCHAR(64)   NOT NULL,
    source_event_type  VARCHAR(64)   NOT NULL,
    membership_id      VARCHAR(36)   NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL,
    read_at            TIMESTAMPTZ,
    version            BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_notification_type   CHECK (type IN ('WELCOME', 'CANCELLATION')),
    CONSTRAINT ck_notification_status CHECK (status IN ('UNREAD', 'READ')),
    CONSTRAINT uq_notification_source_event UNIQUE (source_event_id)
);
-- Inbox query: recipient-scoped (tenant + account), newest-first, optional
-- status filter.
CREATE INDEX idx_notifications_inbox
    ON notifications (tenant_id, account_id, created_at DESC);
CREATE INDEX idx_notifications_inbox_status
    ON notifications (tenant_id, account_id, status);

-- ---------------------------------------------------------------------------
-- processed_events — consumer idempotency dedupe store (architecture.md §
-- Idempotency). Keyed on the envelope eventId; a duplicate eventId is skipped
-- without creating a second notification so re-delivery leaves the inbox
-- byte-identical.
--
-- Same column shape as libs:java-messaging ProcessedEventJpaEntity
-- (event_id / event_type / processed_at). Declared service-locally (not bound to
-- the lib repository) because this service excludes OutboxAutoConfiguration —
-- importing the lib's ProcessedEventJpaRepository would also drag the lib's
-- OutboxJpaRepository, whose outbox entity has no table here (feedback §13;
-- erp notification-service precedent).
-- ---------------------------------------------------------------------------
CREATE TABLE processed_events (
    event_id        VARCHAR(64)  PRIMARY KEY,
    event_type      VARCHAR(100) NOT NULL,
    processed_at    TIMESTAMPTZ  NOT NULL
);
