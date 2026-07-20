-- TASK-BE-536 — POST /api/promotions/{promotionId}/coupons/issue has no client
-- key, no dedupe. CouponCommandService.issueCoupons mints a fresh UUID per userId
-- via Coupon.issue on every call, and Promotion.validateCanIssue caps only the
-- TOTAL issued count — it does not detect "this exact batch was already issued".
-- A replayed request issues an entire second batch of coupons until
-- maxIssuanceCount is hit.
--
-- No natural key is used: the same userId appearing twice across two DIFFERENT
-- issue calls can be a legitimate re-issuance (e.g. a promo re-run), so only a
-- client-supplied key can tell "a retry of the batch I already issued" apart from
-- "a second, intentional issuance to the same users". Scoped to promotionId — the
-- key namespace is per-promotion.
--
-- One row per accepted issuance request. UNIQUE (promotion_id, idempotency_key) is:
--   * the replay key — a same-key retry finds this row and returns the ALREADY
--     issued count without minting a second batch of coupons; and
--   * the CONCURRENCY backstop — two simultaneous duplicates both miss the
--     SELECT, but only one INSERT can commit. The loser gets
--     DataIntegrityViolationException -> 409 IDEMPOTENCY_KEY_CONFLICT.
--
-- `user_ids_digest` is recorded so a same-key replay with a DIFFERENT user batch
-- is rejected (409) rather than silently returning the first batch's result. It is
-- a digest (not the raw list) to bound row size for large batches; comparison is
-- byte-equality on the digest, so key order matters (an operator resubmitting
-- from the same UI form naturally reproduces the same order).
--
-- NO TTL, deliberately (mirrors payment_refund_request, TASK-BE-535).

CREATE TABLE coupon_issue_request (
    id              BIGSERIAL    PRIMARY KEY,
    promotion_id    VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    user_ids_digest VARCHAR(64)  NOT NULL,
    issued_count    INT          NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_coupon_issue_request_key UNIQUE (promotion_id, idempotency_key)
);

CREATE INDEX idx_coupon_issue_request_promotion ON coupon_issue_request (promotion_id, created_at);
