-- iam-platform account-service outbox v1 -> v2 (TASK-BE-451).
-- MySQL 8, InnoDB, utf8mb4 — parity with V1. Migrates account-service onto the
-- shared v2 AbstractOutboxPublisher (the OutboxRow path, ADR-MONO-004 § 5),
-- mirroring the finance account-service MySQL precedent (TASK-FIN-BE-045) and the
-- erp approval-service relay/metric pattern (TASK-ERP-BE-025).
--
-- account_outbox — pending rows have published_at IS NULL; the relay
-- (AccountOutboxPublisher) forwards them to Kafka in created-order and stamps
-- published_at after the ACK (at-least-once; downstream dedupe on the eventId
-- where the flat payload carries one). id is the UUIDv7 event id stored as its
-- 36-char canonical string — matches AccountOutboxJpaEntity's UUID field mapped
-- via @JdbcTypeCode(SqlTypes.CHAR). payload is the v1 *flat* wire JSON (TEXT — the
-- MySQL precedent, NOT jsonb).
--
-- IMPORTANT — FLAT wire shape (NOT the 7-field envelope). account-service's v1
-- publishers used BaseEventPublisher.saveEvent (serialize-as-is, NO envelope), so
-- the on-wire Kafka value is the bare payload map at the JSON root. TASK-BE-422/423
-- contractually locked this flat shape (ecommerce account.* consumers parse top-level
-- fields). The v2 write adapters reproduce the EXACT v1 flat bytes — no double-wrap.
--
-- aggregate_id VARCHAR(64): fits both the account.* key (accountId UUID, 36) and the
-- tenant.subscription.changed composite key "<tenantId>:<domainKey>" (the longest
-- realistic value is well under 64 — tenant slugs + domain keys are short identifiers).
-- event_type VARCHAR(80): fits "tenant.subscription.changed" (27).
-- NB: no event_version column (the iam v1 path never carried one — matching erp approval).
CREATE TABLE account_outbox (
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
CREATE INDEX idx_account_outbox_unpublished ON account_outbox (published_at, created_at);

-- KEEP-auto-config stance (NOT the finance account exclude stance). The v1 `outbox`
-- (BIGINT/status) and `processed_events` tables from V0003/V0005 are intentionally
-- NOT dropped: account-service RETAINS the libs OutboxAutoConfiguration (it is NOT
-- excluded), so the libs OutboxJpaEntity / ProcessedEventJpaEntity remain
-- EntityScanned and hibernate.ddl-auto=validate still requires both tables present.
-- They remain as present-but-unused stubs (droppable by a later cleanup task once the
-- lib stops force-scanning the v1 entity). The v2 write adapters no longer use the lib
-- OutboxWriter and the v2 relay no longer drives the v1 `outbox`. Any unpublished rows
-- left in the v1 `outbox` at cutover are abandoned (low-volume, re-derivable in
-- demo/fed-e2e/CI — TASK-BE-451 Edge Case "cutover" / F1).
