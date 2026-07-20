package com.example.product.domain.repository;

import com.example.product.domain.model.StockAdjustmentRequest;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for accepted stock-adjustment-request records — the idempotency
 * store for {@code PATCH /api/admin/products/{productId}/stock} (TASK-BE-536).
 *
 * <p><b>Fail-closed by construction.</b> The backing store is product-service's own
 * Postgres, written inside the adjustment's own transaction — not a separate Redis
 * or lock service. There is no "store unavailable" mode in which this guard could
 * silently degrade to fail-open while adjustments keep flowing: if the database is
 * unreachable the adjustment transaction itself fails and no stock moves. Mirrors
 * payment-service's {@code RefundRequestRepository} (TASK-BE-535).
 */
public interface StockAdjustmentRequestRepository {

    /** The previously-accepted request for {@code (variantId, idempotencyKey)}, if any. */
    Optional<StockAdjustmentRequest> find(UUID variantId, String idempotencyKey);

    /**
     * Inserts an accepted request and <b>flushes immediately</b>, so a violation of
     * {@code UNIQUE (variant_id, idempotency_key)} surfaces as a
     * {@code DataIntegrityViolationException} at this call — inside the caller's
     * {@code try} — instead of being deferred to transaction commit, where it could
     * no longer be translated into a 409 and would escape as a 500.
     *
     * <p>This is what makes the guard hold under <em>concurrent</em> duplicates
     * rather than only sequential replays: both callers may miss {@link #find}, but
     * only one insert commits.
     */
    StockAdjustmentRequest insert(StockAdjustmentRequest request);
}
