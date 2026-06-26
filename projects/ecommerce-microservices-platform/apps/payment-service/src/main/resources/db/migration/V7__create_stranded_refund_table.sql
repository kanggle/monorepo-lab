-- TASK-BE-438 — stranded-refund auto-reconciliation sweeper (ADR-MONO-005 § 2.3 D3, Category A).
--
-- Durable, queryable record of a captured-but-not-reversed refund (the TASK-BE-437
-- escalation). The PaymentRefundStrandedRecorder writes one row per stranding in the
-- SAME REQUIRES_NEW transaction as the PaymentRefundStranded escalation outbox event
-- (so both survive the confirm() rollback). StrandedRefundSweeper polls
-- status='STRANDED' AND next_attempt_at <= now and reconciles each through the PG
-- (PG-state-first, double-refund-safe). Lifecycle:
--   STRANDED ──auto-resolved (PG already CANCELED, or re-cancel succeeds)──▶ RESOLVED  (terminal)
--   STRANDED ──attempts >= cap, or definitive 4xx PG reject────────────────▶ UNRESOLVED (terminal, re-escalates)

CREATE TABLE stranded_refund (
    id              BIGSERIAL       PRIMARY KEY,
    payment_id      VARCHAR(255)    NOT NULL,
    order_id        VARCHAR(255)    NOT NULL,
    payment_key     VARCHAR(255),
    amount          BIGINT          NOT NULL,
    reason          VARCHAR(255)    NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'STRANDED',
    attempts        INTEGER         NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP       NOT NULL,
    last_error      VARCHAR(1000),
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    resolved_at     TIMESTAMP
);

-- Sweeper poll predicate: status + due time. Resolved/Unresolved rows are excluded by the
-- status filter so a terminal record is never re-selected (terminal is terminal).
CREATE INDEX idx_stranded_refund_status_next_attempt ON stranded_refund (status, next_attempt_at);

-- Dedupe at stranding time (Edge Case "Record dedupe"): a client retrying the same failed
-- confirm re-enters the BE-437 catch path. At most ONE open (STRANDED) obligation may exist
-- per payment_id so the sweeper sees one obligation, not N. A payment that was resolved and
-- later strands again is permitted (partial index only constrains open rows).
CREATE UNIQUE INDEX uq_stranded_refund_open_payment ON stranded_refund (payment_id) WHERE status = 'STRANDED';
