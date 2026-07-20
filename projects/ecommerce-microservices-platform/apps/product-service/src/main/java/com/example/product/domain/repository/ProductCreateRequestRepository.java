package com.example.product.domain.repository;

import com.example.product.domain.model.ProductCreateRequest;

import java.util.Optional;

/**
 * Persistence port for accepted product-registration-request records — the
 * idempotency store for {@code POST /api/admin/products} (TASK-BE-536).
 *
 * <p><b>Fail-closed by construction.</b> The backing store is product-service's
 * own Postgres, written inside the registration's own transaction. Mirrors
 * payment-service's {@code RefundRequestRepository} (TASK-BE-535).
 */
public interface ProductCreateRequestRepository {

    /** The previously-accepted request for {@code (tenantId, idempotencyKey)}, if any. */
    Optional<ProductCreateRequest> find(String tenantId, String idempotencyKey);

    /**
     * Inserts an accepted request and <b>flushes immediately</b>, so a violation of
     * {@code UNIQUE (tenant_id, idempotency_key)} surfaces as a
     * {@code DataIntegrityViolationException} at this call — inside the caller's
     * {@code try} — instead of being deferred to transaction commit.
     *
     * <p>This is what makes the guard hold under <em>concurrent</em> duplicates
     * rather than only sequential replays: both callers may miss {@link #find}, but
     * only one insert commits.
     */
    ProductCreateRequest insert(ProductCreateRequest request);
}
