package com.example.settlement.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of the append-only commission ledger ({@code commission_accrual}) — a
 * single {@code (order line × event)}. An {@link AccrualType#ACCRUAL} row is
 * positive; a {@link AccrualType#REVERSAL} row is the negation of its order's
 * accruals. Immutable (F3) — a correction is a new reversal row, never an update.
 *
 * <p>{@code tenantId} is derived from the {@code OrderPlaced} snapshot (AC-7);
 * {@code sellerId} from the snapshot line (Step 3 attribution).
 */
public record CommissionAccrual(
        String accrualId,
        String tenantId,
        String orderId,
        String paymentId,
        String sellerId,
        AccrualType type,
        long grossMinor,
        int rateBps,
        long commissionMinor,
        long sellerNetMinor,
        Instant occurredAt,
        /**
         * For a {@link AccrualType#REVERSAL} row, the {@code accrualId} of the ACCRUAL it
         * (partially) reverses — lets per-accrual cumulative reversed be computed exactly
         * across multiple partial refunds. {@code null} on ACCRUAL rows (and on legacy
         * REVERSAL rows written before partial refunds).
         */
        String reversesAccrualId) {

    /** Convenience constructor for ACCRUAL-style rows with no parent reversal link. */
    public CommissionAccrual(String accrualId, String tenantId, String orderId, String paymentId,
                             String sellerId, AccrualType type, long grossMinor, int rateBps,
                             long commissionMinor, long sellerNetMinor, Instant occurredAt) {
        this(accrualId, tenantId, orderId, paymentId, sellerId, type, grossMinor, rateBps,
                commissionMinor, sellerNetMinor, occurredAt, null);
    }

    /** Builds an ACCRUAL row from a computed split (a fresh UUID id). */
    public static CommissionAccrual accrual(String tenantId, String orderId, String paymentId,
                                            String sellerId, CommissionSplit split, Instant occurredAt) {
        return new CommissionAccrual(UUID.randomUUID().toString(), tenantId, orderId, paymentId,
                sellerId, AccrualType.ACCRUAL, split.grossMinor(), split.rateBps(),
                split.commissionMinor(), split.sellerNetMinor(), occurredAt);
    }

    /**
     * Builds a REVERSAL row clawing back {@code reverseSplit} (a <b>pre-negated</b> split —
     * negative gross / commission / seller_net) of this accrual, linked back to it via
     * {@code reversesAccrualId}. For a full refund the caller passes the exact remaining;
     * for a partial, the proportional portion. The split's own invariant
     * ({@code commission + seller_net == gross}) guarantees the DB
     * {@code ck_commission_accrual_split} constraint holds on the reversal row. The
     * original accrual is never mutated (F3).
     */
    public CommissionAccrual toReversal(String refundPaymentId, Instant occurredAt, CommissionSplit reverseSplit) {
        return new CommissionAccrual(UUID.randomUUID().toString(), tenantId, orderId, refundPaymentId,
                sellerId, AccrualType.REVERSAL, reverseSplit.grossMinor(), rateBps,
                reverseSplit.commissionMinor(), reverseSplit.sellerNetMinor(), occurredAt, this.accrualId);
    }
}
