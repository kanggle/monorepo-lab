package com.example.promotion.domain.coupon;

import java.util.Optional;

/**
 * Persistence port for accepted coupon-issuance-request records — the idempotency
 * store for {@code POST /api/promotions/{promotionId}/coupons/issue} (TASK-BE-536).
 *
 * <p><b>Fail-closed by construction.</b> The backing store is promotion-service's
 * own Postgres, written inside the issuance's own transaction. Mirrors
 * payment-service's {@code RefundRequestRepository} (TASK-BE-535).
 */
public interface CouponIssueRequestRepository {

    /** The previously-accepted request for {@code (promotionId, idempotencyKey)}, if any. */
    Optional<CouponIssueRequest> find(String promotionId, String idempotencyKey);

    /**
     * Inserts an accepted request and <b>flushes immediately</b>, so a violation of
     * {@code UNIQUE (promotion_id, idempotency_key)} surfaces as a
     * {@code DataIntegrityViolationException} at this call — inside the caller's
     * {@code try} — instead of being deferred to transaction commit.
     *
     * <p>This is what makes the guard hold under <em>concurrent</em> duplicates
     * rather than only sequential replays: both callers may miss {@link #find}, but
     * only one insert commits.
     */
    CouponIssueRequest insert(CouponIssueRequest request);
}
