package com.example.payment.domain.model;

import java.time.Instant;

/**
 * Durable, queryable record of a stranded refund (TASK-BE-438).
 *
 * <p>Created when {@code PaymentConfirmService.confirm()} captures funds for a concurrently
 * cancelled order and then <b>fails to reverse them at the PG</b> (the TASK-BE-437 escalation
 * path). One row is persisted per stranding in the same {@code REQUIRES_NEW} transaction as the
 * {@code PaymentRefundStranded} escalation event, so the queryable obligation and the alert
 * commit atomically across the {@code confirm()} rollback.
 *
 * <p>The {@code StrandedRefundSweeper} polls open ({@code STRANDED}) rows whose
 * {@code nextAttemptAt} is due and reconciles each: it checks the actual PG state first
 * (double-refund guard — F1), then either resolves (already cancelled / re-cancel succeeds),
 * backs off (transient PG failure), or terminates to {@code UNRESOLVED} (attempt cap or a
 * definitive PG rejection). State transitions are expressed as the methods below; the
 * backoff schedule and {@code Clock} live in the reconciler so this model stays pure.
 */
public class StrandedRefund {

    private Long id;
    private String paymentId;
    private String orderId;
    private String paymentKey;
    private long amount;
    private String reason;
    private StrandedRefundStatus status;
    private int attempts;
    private Instant nextAttemptAt;
    private String lastError;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant resolvedAt;

    private StrandedRefund() {
    }

    /** New open obligation at stranding time: {@code STRANDED}, zero attempts, due immediately. */
    public static StrandedRefund open(String paymentId, String orderId, String paymentKey,
                                      long amount, String reason, Instant now) {
        StrandedRefund r = new StrandedRefund();
        r.paymentId = paymentId;
        r.orderId = orderId;
        r.paymentKey = paymentKey;
        r.amount = amount;
        r.reason = reason;
        r.status = StrandedRefundStatus.STRANDED;
        r.attempts = 0;
        r.nextAttemptAt = now;
        r.createdAt = now;
        r.updatedAt = now;
        return r;
    }

    /** Rehydrate from persistence. */
    public static StrandedRefund reconstitute(Long id, String paymentId, String orderId,
                                              String paymentKey, long amount, String reason,
                                              StrandedRefundStatus status, int attempts,
                                              Instant nextAttemptAt, String lastError,
                                              Instant createdAt, Instant updatedAt,
                                              Instant resolvedAt) {
        StrandedRefund r = new StrandedRefund();
        r.id = id;
        r.paymentId = paymentId;
        r.orderId = orderId;
        r.paymentKey = paymentKey;
        r.amount = amount;
        r.reason = reason;
        r.status = status;
        r.attempts = attempts;
        r.nextAttemptAt = nextAttemptAt;
        r.lastError = lastError;
        r.createdAt = createdAt;
        r.updatedAt = updatedAt;
        r.resolvedAt = resolvedAt;
        return r;
    }

    /**
     * Auto-healed terminal: the PG was already cancelled, or a retry cancel succeeded.
     * Idempotent on an already-{@code RESOLVED} row; rejects re-resolving a terminal
     * {@code UNRESOLVED} row (terminal is terminal).
     */
    public void markResolved(Instant now) {
        if (this.status == StrandedRefundStatus.RESOLVED) {
            return;
        }
        requireOpen("resolve");
        this.status = StrandedRefundStatus.RESOLVED;
        this.resolvedAt = now;
        this.updatedAt = now;
    }

    /**
     * Transient PG failure (or an ambiguous PG-state fetch): increment the attempt counter,
     * push the next attempt out by the caller-computed backoff instant, and stay {@code STRANDED}
     * so the next due tick retries. Never infers resolution from an error.
     */
    public void recordTransientFailure(Instant now, Instant nextAttemptAt, String error) {
        requireOpen("retry");
        this.attempts += 1;
        this.nextAttemptAt = nextAttemptAt;
        this.lastError = error;
        this.updatedAt = now;
    }

    /**
     * Terminal failure (ADR-MONO-005 § 2.3 D3, F2/F3): the attempt cap is exhausted or the PG
     * issued a definitive rejection of the cancel. Stops auto-retrying; the caller re-emits the
     * terminal escalation. {@code resolvedAt} stays null — this is NOT a money-safe resolution.
     */
    public void markUnresolved(Instant now, String error) {
        requireOpen("terminate");
        this.attempts += 1;
        this.status = StrandedRefundStatus.UNRESOLVED;
        this.lastError = error;
        this.updatedAt = now;
    }

    /** True once {@code attempts + 1} would reach the cap — i.e. this retry is the last one. */
    public boolean wouldExhaust(int maxAttempts) {
        return this.attempts + 1 >= maxAttempts;
    }

    public boolean isOpen() {
        return this.status == StrandedRefundStatus.STRANDED;
    }

    private void requireOpen(String action) {
        if (this.status != StrandedRefundStatus.STRANDED) {
            throw new IllegalStateException(
                    "Cannot " + action + " a non-STRANDED stranded_refund (status=" + status
                            + ", paymentId=" + paymentId + ")");
        }
    }

    public Long getId() {
        return id;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getPaymentKey() {
        return paymentKey;
    }

    public long getAmount() {
        return amount;
    }

    public String getReason() {
        return reason;
    }

    public StrandedRefundStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }
}
