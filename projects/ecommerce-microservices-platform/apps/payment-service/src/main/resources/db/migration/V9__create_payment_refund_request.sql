-- TASK-BE-535 — a duplicated PARTIAL refund pays out twice.
--
-- Payment.refund(long) validates only 0 < amount <= remainingRefundable and then
-- ACCUMULATES refundedAmount. Two identical POST /api/payments/{id}/refund {amount:1000}
-- against a 10000 payment left refundedAmount = 2000 — a genuine double payout. And it
-- is indistinguishable from a LEGITIMATE second partial refund, which is why no
-- server-side natural key can fix it alone: only the client knows whether request #2 is
-- "the retry of #1" or "a second, intentional refund". Hence a client key.
--
-- (The zero-arg Payment.refund() full path was already idempotent — it early-returns
-- when the payment is REFUNDED — so the OrderCancelled event path is untouched.)
--
-- One row per accepted refund request. UNIQUE (payment_id, idempotency_key) is:
--   * the replay key — a same-key retry finds this row and returns the payment's current
--     state instead of accumulating again, re-calling the PG, or re-publishing
--     PaymentRefunded; and
--   * the CONCURRENCY backstop (AC-5) — two simultaneous duplicates both miss the
--     SELECT, but only one INSERT can commit. The loser gets
--     DataIntegrityViolationException → 409 IDEMPOTENCY_KEY_CONFLICT, and its retry finds
--     the winner's row and replays. A read-then-write check with no constraint behind it
--     would let both through.
--
-- The key is scoped to payment_id, not global: the same key value against a different
-- payment is a different request.
--
-- NO TTL / retention sweep, deliberately (Edge Case "Retention"): an idempotency record
-- that expires before the caller's retry window closes re-opens the double-payout hole.
-- These rows are small and bounded by refund volume, so they are kept indefinitely.
--
-- FAIL-CLOSED (F3): the dedupe store is this service's own Postgres, written in the SAME
-- transaction as the refund — not a separate Redis/lock store that could be down while
-- refunds keep flowing. There is no outage mode where the guard degrades to fail-open:
-- if the DB is unreachable the refund itself fails and no money moves.

CREATE TABLE payment_refund_request (
    id              BIGSERIAL    PRIMARY KEY,
    payment_id      VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    amount          BIGINT       NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_payment_refund_request_key UNIQUE (payment_id, idempotency_key)
);

-- Replay lookup is served by the unique index above; this one supports operational
-- "show me every refund request for this payment" reads in key order.
CREATE INDEX idx_payment_refund_request_payment ON payment_refund_request (payment_id, created_at);
