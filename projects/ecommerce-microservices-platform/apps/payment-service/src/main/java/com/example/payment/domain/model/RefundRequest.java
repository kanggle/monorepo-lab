package com.example.payment.domain.model;

import java.time.Instant;

/**
 * A refund request that was accepted for one payment under one client
 * {@code Idempotency-Key} (TASK-BE-535, Flyway V9 {@code payment_refund_request}).
 *
 * <p>Exists so the HTTP partial-refund path can tell "a retry of the refund I already
 * performed" apart from "a second, intentional partial refund" — the two are otherwise
 * byte-identical requests, and {@link Payment#refund(long)} accumulates, so guessing
 * wrong pays out twice (or blocks a legitimate refund).
 *
 * <p>{@code amount} is recorded because the key is bound to it: a replay of the same key
 * with a <em>different</em> amount is rejected (409) rather than silently replayed.
 *
 * <p>Immutable — a request record is never mutated after acceptance. Records are kept
 * indefinitely (no TTL): an expiring record would re-open the double-payout window for a
 * client whose retry policy outlives it.
 */
public final class RefundRequest {

    private final Long id;
    private final String paymentId;
    private final String idempotencyKey;
    private final long amount;
    private final Instant createdAt;

    private RefundRequest(Long id, String paymentId, String idempotencyKey,
                          long amount, Instant createdAt) {
        this.id = id;
        this.paymentId = paymentId;
        this.idempotencyKey = idempotencyKey;
        this.amount = amount;
        this.createdAt = createdAt;
    }

    /** A not-yet-persisted record for an incoming refund request. */
    public static RefundRequest of(String paymentId, String idempotencyKey, long amount,
                                   Instant createdAt) {
        return new RefundRequest(null, paymentId, idempotencyKey, amount, createdAt);
    }

    /** Rehydrates a persisted record. */
    public static RefundRequest reconstitute(Long id, String paymentId, String idempotencyKey,
                                             long amount, Instant createdAt) {
        return new RefundRequest(id, paymentId, idempotencyKey, amount, createdAt);
    }

    /** True iff this recorded request was for exactly {@code candidateAmount}. */
    public boolean matchesAmount(long candidateAmount) {
        return this.amount == candidateAmount;
    }

    public Long getId() {
        return id;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public long getAmount() {
        return amount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
