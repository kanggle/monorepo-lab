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
        Instant occurredAt) {

    /** Builds an ACCRUAL row from a computed split (a fresh UUID id). */
    public static CommissionAccrual accrual(String tenantId, String orderId, String paymentId,
                                            String sellerId, CommissionSplit split, Instant occurredAt) {
        return new CommissionAccrual(UUID.randomUUID().toString(), tenantId, orderId, paymentId,
                sellerId, AccrualType.ACCRUAL, split.grossMinor(), split.rateBps(),
                split.commissionMinor(), split.sellerNetMinor(), occurredAt);
    }

    /**
     * Builds the REVERSAL row that negates this accrual — same attribution keys,
     * negated amounts, the refund's {@code paymentId} and time. The original row is
     * never mutated (F3).
     */
    public CommissionAccrual toReversal(String refundPaymentId, Instant occurredAt) {
        return new CommissionAccrual(UUID.randomUUID().toString(), tenantId, orderId, refundPaymentId,
                sellerId, AccrualType.REVERSAL, -grossMinor, rateBps,
                -commissionMinor, -sellerNetMinor, occurredAt);
    }
}
