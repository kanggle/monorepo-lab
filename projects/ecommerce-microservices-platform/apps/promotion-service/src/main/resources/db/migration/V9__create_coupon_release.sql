-- TASK-INT-027 — order-service registers its coupon release BEFORE it calls apply, and sends
-- that release when the placement transaction does not commit. But the two calls have no
-- guaranteed order: the release is sent precisely because the apply timed out on the caller,
-- which is exactly when that apply may still be executing here. Measured (AC-0): the release
-- arrives, finds the coupon still ISSUED, does nothing AND RECORDS NOTHING, and the late apply
-- then commits — binding the coupon to an order that was never saved.
--
-- Such a coupon is stranded. Nothing frees it: the cancellation restore path
-- (order.order.cancelled -> restoreCouponsByOrderId) needs an order to fire, and there is none.
-- It sits USED until it expires, and the user is told COUPON_ALREADY_USED.
--
-- One row per (coupon_id, order_id) whose release arrived with nothing to give back. The row is
-- the FENCE: a later apply for that same pair is refused (422 COUPON_PLACEMENT_RELEASED) instead
-- of using the coupon. UNIQUE (coupon_id, order_id) is both
--   * the idempotency key — a retried release writes one row, not two; and
--   * the concurrency backstop — two simultaneous releases both miss the SELECT, only one
--     INSERT commits, and the loser treats the violation as "already recorded", not an error.
--
-- Scoped to the PAIR, never the coupon: a placement that failed must not cost the user their
-- coupon, so the same coupon on a DIFFERENT order_id is untouched by this table.
--
-- promotion-service does not ask order-service whether the order exists — that would invert the
-- dependency (order -> promotion is the only allowed direction). The fence uses only what the
-- release call already carried.
--
-- NO TTL, deliberately — mirrors coupon_issue_request (V8, TASK-BE-536) and processed_events
-- (V5). A pruned row would silently re-open the window for any apply still in flight.

CREATE TABLE coupon_release (
    id         BIGSERIAL    PRIMARY KEY,
    coupon_id  VARCHAR(255) NOT NULL,
    order_id   VARCHAR(255) NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_coupon_release_pair UNIQUE (coupon_id, order_id)
);

CREATE INDEX idx_coupon_release_created_at ON coupon_release (created_at);
