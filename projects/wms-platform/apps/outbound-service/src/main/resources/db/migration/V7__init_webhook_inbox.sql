-- ERP order webhook ingest buffer + replay-protection table.
-- Authoritative reference: specs/services/outbound-service/domain-model.md §9.
-- Authoritative reference: specs/contracts/webhooks/erp-order-webhook.md § Replay Dedupe.
--
-- erp_order_webhook_dedupe is append-only — V8 enforces by revoking
-- UPDATE/DELETE for the application role.

CREATE TABLE erp_order_webhook_inbox (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id      VARCHAR(80) NOT NULL,
    source        VARCHAR(50) NOT NULL,
    payload       JSONB NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    received_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at  TIMESTAMPTZ
);

CREATE TABLE erp_order_webhook_dedupe (
    event_id      VARCHAR(80) PRIMARY KEY,
    source        VARCHAR(50) NOT NULL,
    received_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_erp_order_webhook_inbox_status ON erp_order_webhook_inbox(status)
    WHERE status = 'PENDING';
