-- TASK-BE-049: TMS request dedupe table.
--
-- Local fallback for vendor idempotency (integration-heavy I4): if the TMS
-- vendor honours the `Idempotency-Key` header, this table also caches the
-- response so a re-attempt skips the network call entirely. If the vendor
-- regresses and stops honouring the header, this table becomes the
-- ground-truth and prevents duplicate side effects on the WMS side.
--
-- Schema declared in `external-integrations.md` §2.7. Each row is keyed by
-- `request_id` = `Shipment.id` (UUIDv7), so insert order is monotonically
-- increasing in time.
--
-- The row is inserted in a separate REQUIRES_NEW transaction, AFTER the
-- saga TX commits and AFTER the TMS network call returns 2xx. A failed TMS
-- call must NOT insert here — that would cement a failure as "already
-- sent".

CREATE TABLE IF NOT EXISTS tms_request_dedupe (
    request_id        UUID                     PRIMARY KEY,
    sent_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    response_snapshot JSONB                    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_tms_request_dedupe_sent_at
    ON tms_request_dedupe (sent_at);

COMMENT ON TABLE tms_request_dedupe
    IS 'TASK-BE-049: cached TMS acknowledgement keyed by shipment id; vendor-idempotency fallback (I4)';
COMMENT ON COLUMN tms_request_dedupe.request_id
    IS 'Equals Shipment.id (UUIDv7) — same value sent as Idempotency-Key header to TMS';
COMMENT ON COLUMN tms_request_dedupe.sent_at
    IS 'Wall-clock at first successful TMS ack; not updated on subsequent re-reads';
COMMENT ON COLUMN tms_request_dedupe.response_snapshot
    IS 'JSONB snapshot of the TmsAcknowledgement (success flag + vendor request id + tracking no + carrier code + status)';
