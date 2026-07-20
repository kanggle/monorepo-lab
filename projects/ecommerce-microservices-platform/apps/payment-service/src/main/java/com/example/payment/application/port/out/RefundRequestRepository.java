package com.example.payment.application.port.out;

import com.example.payment.domain.model.RefundRequest;

import java.util.Optional;

/**
 * Persistence port for accepted refund-request records — the idempotency store for the
 * HTTP partial-refund path (TASK-BE-535).
 *
 * <p><b>Fail-closed by construction (AC-5 / F3).</b> The backing store is
 * payment-service's own Postgres, written inside the refund's own transaction — not a
 * separate Redis or lock service. There is therefore no "store unavailable" mode in which
 * this guard could silently degrade to fail-open while refunds keep flowing: if the
 * database is unreachable the refund transaction itself fails and no money moves. That is
 * a deliberate divergence from the shared wms {@code IdempotencyKeyFilter}, which is
 * fail-open for availability — an availability-first calculus is wrong on a funds-out
 * endpoint.
 */
public interface RefundRequestRepository {

    /** The previously-accepted request for {@code (paymentId, idempotencyKey)}, if any. */
    Optional<RefundRequest> find(String paymentId, String idempotencyKey);

    /**
     * Inserts an accepted request and <b>flushes immediately</b>, so a violation of
     * {@code UNIQUE (payment_id, idempotency_key)} surfaces as a
     * {@code DataIntegrityViolationException} at this call — inside the caller's
     * {@code try} — instead of being deferred to transaction commit, where it could no
     * longer be translated into a 409 and would escape as a 500.
     *
     * <p>This is what makes the guard hold under <em>concurrent</em> duplicates rather
     * than only sequential replays: both callers may miss {@link #find}, but only one
     * insert commits.
     */
    RefundRequest insert(RefundRequest refundRequest);
}
