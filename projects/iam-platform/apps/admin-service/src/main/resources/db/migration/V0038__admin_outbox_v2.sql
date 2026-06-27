-- iam-platform admin-service outbox v1 -> v2 (TASK-BE-452).
-- MySQL 8, InnoDB, utf8mb4 — parity with V1. Migrates admin-service onto the
-- shared v2 AbstractOutboxPublisher (the OutboxRow path, ADR-MONO-004 § 5),
-- mirroring the finance account-service MySQL precedent (TASK-FIN-BE-045) and the
-- erp approval-service relay/metric pattern (TASK-ERP-BE-025).
--
-- admin_outbox — pending rows have published_at IS NULL; the relay
-- (AdminOutboxPublisher) forwards them to Kafka in created-order and stamps
-- published_at after the ACK (at-least-once; downstream dedupe on the eventId).
-- id is the UUIDv7 event id stored as its 36-char canonical string — matches
-- AdminOutboxJpaEntity's UUID field mapped via @JdbcTypeCode(SqlTypes.CHAR).
--
-- TWO publishers, ONE table. Both AdminEventPublisher (admin.action.performed —
-- FLAT payload via saveEvent) and TenantEventPublisher (tenant.created/suspended/
-- reactivated/updated — a SELF-BUILT 7-field envelope via saveEvent) write into
-- this single admin_outbox. The v2 write adapters reproduce each publisher's EXACT
-- v1 serialized bytes (admin.action = flat; tenant.* = the full self-built envelope)
-- — NO double-wrap.
--
-- event_type VARCHAR(80) fits "admin.action.performed" (22) / "tenant.reactivated" (18).
-- aggregate_id VARCHAR(64) fits the tenantId / accountId / "-" keys.
-- NB: no event_version column (the iam v1 path never carried one — matching erp approval).
CREATE TABLE admin_outbox (
    id              CHAR(36)     NOT NULL,
    aggregate_type  VARCHAR(60)  NOT NULL,
    aggregate_id    VARCHAR(64)  NOT NULL,
    event_type      VARCHAR(80)  NOT NULL,
    payload         TEXT         NOT NULL,
    partition_key   VARCHAR(64)  NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    published_at    DATETIME(6),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_admin_outbox_unpublished ON admin_outbox (published_at, created_at);

-- KEEP-auto-config stance (NOT the finance account exclude stance). The v1 `outbox`
-- (BIGINT/status) and `processed_events` tables from V0002/V0016 are intentionally
-- NOT dropped: admin-service RETAINS the libs OutboxAutoConfiguration (it is NOT
-- excluded), so the libs OutboxJpaEntity / ProcessedEventJpaEntity remain
-- EntityScanned and hibernate.ddl-auto=validate still requires both tables present.
-- They remain as present-but-unused stubs (droppable by a later cleanup task once the
-- lib stops force-scanning the v1 entity). The v2 write adapters no longer use the lib
-- OutboxWriter and the v2 relay no longer drives the v1 `outbox`. Any unpublished rows
-- left in the v1 `outbox` at cutover are abandoned (low-volume, re-derivable in
-- demo/fed-e2e/CI — TASK-BE-452 Edge Case "cutover" / F1).
