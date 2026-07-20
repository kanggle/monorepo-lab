package com.example.product.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A stock-adjustment request that was accepted for one variant under one client
 * {@code Idempotency-Key} (TASK-BE-536, Flyway V17 {@code stock_adjustment_request}).
 *
 * <p>Exists so {@code PATCH /api/admin/products/{productId}/stock} can tell "a retry
 * of the adjustment I already performed" apart from "a second, intentional
 * adjustment" — the two are otherwise byte-identical requests (a genuine "+10
 * received twice" warehouse event is real; see the task's Edge Cases), and
 * {@code Inventory.adjustStock} accumulates, so guessing wrong double-adjusts stock.
 *
 * <p>{@code quantity} is recorded because the key is bound to it: a replay of the
 * same key with a <em>different</em> quantity is rejected (409) rather than silently
 * replayed.
 *
 * <p>Immutable — a request record is never mutated after acceptance. Kept
 * indefinitely (no TTL): an expiring record would re-open the double-adjustment
 * window for a client whose retry policy outlives it.
 */
public final class StockAdjustmentRequest {

    private final Long id;
    private final UUID variantId;
    private final String idempotencyKey;
    private final int quantity;
    private final Instant createdAt;

    private StockAdjustmentRequest(Long id, UUID variantId, String idempotencyKey,
                                   int quantity, Instant createdAt) {
        this.id = id;
        this.variantId = variantId;
        this.idempotencyKey = idempotencyKey;
        this.quantity = quantity;
        this.createdAt = createdAt;
    }

    /** A not-yet-persisted record for an incoming stock-adjustment request. */
    public static StockAdjustmentRequest of(UUID variantId, String idempotencyKey, int quantity,
                                            Instant createdAt) {
        return new StockAdjustmentRequest(null, variantId, idempotencyKey, quantity, createdAt);
    }

    /** Rehydrates a persisted record. */
    public static StockAdjustmentRequest reconstitute(Long id, UUID variantId, String idempotencyKey,
                                                       int quantity, Instant createdAt) {
        return new StockAdjustmentRequest(id, variantId, idempotencyKey, quantity, createdAt);
    }

    /** True iff this recorded request was for exactly {@code candidateQuantity}. */
    public boolean matchesQuantity(int candidateQuantity) {
        return this.quantity == candidateQuantity;
    }

    public Long getId() {
        return id;
    }

    public UUID getVariantId() {
        return variantId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public int getQuantity() {
        return quantity;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
