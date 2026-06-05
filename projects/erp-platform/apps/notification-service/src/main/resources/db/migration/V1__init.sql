-- erp-platform notification-service initial schema (MySQL 8, InnoDB, utf8mb4).
-- Approval-notification fan-out (first increment). Consumes approval-service's 4
-- transition topics, persists one in-app notification per resolved recipient + a
-- Category C delivery record, and a dedupe row per processed event. Shares the
-- erp_db instance with masterdata / approval (separate tables; no shared-table
-- JOIN). The single source of record for every notified fact is approval-service
-- (E5 — notification is a terminal consumer, no re-emission). TASK-ERP-BE-011.

-- ---------------------------------------------------------------------------
-- notification — one in-app notification per resolved recipient. 'is_read' /
-- 'read_at' is the single mutable state (an in-app read receipt; 'read' is a
-- MySQL reserved word so the column is 'is_read'). source_type = APPROVAL,
-- source_id = approvalRequestId (opaque back-reference; authoritative approval
-- state lives in approval-service).
-- ---------------------------------------------------------------------------
CREATE TABLE notification (
    id              VARCHAR(64)  NOT NULL,
    tenant_id       VARCHAR(64)  NOT NULL DEFAULT 'erp',
    recipient_id    VARCHAR(64)  NOT NULL,
    type            VARCHAR(32)  NOT NULL,
    title           VARCHAR(256) NOT NULL,
    body            VARCHAR(2000) NOT NULL,
    source_type     VARCHAR(32)  NOT NULL,
    source_id       VARCHAR(64)  NOT NULL,
    is_read         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      DATETIME(6)  NOT NULL,
    read_at         DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT ck_notification_type CHECK (type IN (
        'APPROVAL_SUBMITTED','APPROVAL_APPROVED','APPROVAL_REJECTED','APPROVAL_WITHDRAWN')),
    CONSTRAINT ck_notification_source_type CHECK (source_type IN ('APPROVAL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
-- Inbox query: recipient-scoped, newest-first, optional read filter.
CREATE INDEX idx_notification_inbox ON notification (tenant_id, recipient_id, created_at);
CREATE INDEX idx_notification_inbox_read ON notification (tenant_id, recipient_id, is_read);

-- ---------------------------------------------------------------------------
-- notification_delivery — delivery-lifecycle record carrying the ADR-MONO-005
-- Category C structure (status / attempt_count / scheduled_retry_at / version).
-- v1 channel = IN_APP: the row commits straight to DELIVERED, attempt_count=1.
-- The Category C columns are present so the v2 external-channel retry scheduler
-- needs no schema migration. version (T5) guards the v2 concurrent-tick
-- contention (no concurrent writer on the v1 IN_APP path).
-- ---------------------------------------------------------------------------
CREATE TABLE notification_delivery (
    id                  VARCHAR(64)  NOT NULL,
    tenant_id           VARCHAR(64)  NOT NULL DEFAULT 'erp',
    notification_id     VARCHAR(64)  NOT NULL,
    event_id            VARCHAR(64)  NOT NULL,
    channel             VARCHAR(16)  NOT NULL,
    status              VARCHAR(16)  NOT NULL,
    attempt_count       INT          NOT NULL DEFAULT 0,
    scheduled_retry_at  DATETIME(6),
    last_error          VARCHAR(500),
    version             INT          NOT NULL DEFAULT 0,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_notification_delivery_status CHECK (status IN ('PENDING','DELIVERED','FAILED')),
    CONSTRAINT ck_notification_delivery_channel CHECK (channel IN ('IN_APP','SLACK','SMTP'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_notification_delivery_notification ON notification_delivery (notification_id);
CREATE INDEX idx_notification_delivery_status ON notification_delivery (status, scheduled_retry_at);

-- ---------------------------------------------------------------------------
-- processed_events — consumer idempotency dedupe store (T8). Keyed on the
-- envelope eventId; a duplicate eventId is skipped without creating a second
-- notification so re-delivery leaves the inbox byte-identical. This is the
-- dispatch provenance. Distinct from libs/java-messaging's ProcessedEventJpaEntity
-- (whose outbox auto-config is excluded — notification is no-outbox).
-- ---------------------------------------------------------------------------
CREATE TABLE processed_events (
    event_id        VARCHAR(64)  NOT NULL,
    topic           VARCHAR(200) NOT NULL,
    aggregate_id    VARCHAR(64)  NOT NULL,
    processed_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_processed_events_topic ON processed_events (topic, processed_at);
