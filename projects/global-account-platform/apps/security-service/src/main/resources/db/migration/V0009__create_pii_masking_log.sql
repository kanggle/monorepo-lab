-- TASK-BE-258: PII masking idempotency log and audit record.
--
-- pii_masking_log records the event_id of every processed account.deleted
-- (anonymized=true) event. The unique constraint on event_id provides
-- idempotency: a second delivery of the same event finds the row via
-- INSERT IGNORE / duplicate-key detection and skips re-masking.
--
-- This table is separate from processed_events (which tracks auth events)
-- to keep concerns isolated and allow independent retention policies.

CREATE TABLE pii_masking_log (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_id       VARCHAR(36)  NOT NULL,
    tenant_id      VARCHAR(32)  NOT NULL,
    account_id     VARCHAR(36)  NOT NULL,
    masked_at      DATETIME(6)  NOT NULL,
    table_names    VARCHAR(512) NOT NULL COMMENT 'JSON array of table names that were masked',
    created_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT uq_pii_masking_log_event_id UNIQUE (event_id)
);

-- Allow fast lookup by (tenant_id, account_id) for audit queries.
CREATE INDEX idx_pii_masking_log_tenant_account
    ON pii_masking_log (tenant_id, account_id);
