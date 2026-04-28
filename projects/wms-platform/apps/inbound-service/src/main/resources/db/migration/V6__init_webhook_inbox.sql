-- ERP webhook ingest buffer + replay-protection table.
-- Authoritative reference:
--   specs/services/inbound-service/domain-model.md §7 (ErpWebhookInbox), §8 (ErpWebhookDedupe).
--   specs/contracts/webhooks/erp-asn-webhook.md § Replay Dedupe.
--
-- ErpWebhookDedupe is append-only — V7 enforces by revoking UPDATE/DELETE on
-- the table for the application role. ErpWebhookInbox is mutable (status
-- transitions PENDING → APPLIED / FAILED) and stays writable.

-- ---------------------------------------------------------------------------
-- ErpWebhookInbox — ingest buffer (decouples ingest from domain processing)
-- ---------------------------------------------------------------------------
CREATE TABLE erp_webhook_inbox (
    event_id        VARCHAR(80)  PRIMARY KEY,
    raw_payload     JSONB        NOT NULL,
    signature       VARCHAR(100) NOT NULL,
    source          VARCHAR(40)  NOT NULL,
    received_at     TIMESTAMPTZ  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    processed_at    TIMESTAMPTZ,
    failure_reason  VARCHAR(500),
    CONSTRAINT ck_erp_webhook_inbox_status
        CHECK (status IN ('PENDING', 'APPLIED', 'FAILED'))
);

-- Background processor picks pending rows in FIFO order, capped batch.
CREATE INDEX idx_erp_webhook_inbox_pending
    ON erp_webhook_inbox (received_at)
    WHERE status = 'PENDING';

CREATE INDEX idx_erp_webhook_inbox_failed
    ON erp_webhook_inbox (received_at)
    WHERE status = 'FAILED';

-- ---------------------------------------------------------------------------
-- ErpWebhookDedupe — replay protection by X-Erp-Event-Id (append-only)
-- ---------------------------------------------------------------------------
CREATE TABLE erp_webhook_dedupe (
    event_id     VARCHAR(80)  PRIMARY KEY,
    received_at  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_erp_webhook_dedupe_received
    ON erp_webhook_dedupe (received_at);
