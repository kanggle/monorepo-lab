package com.example.settlement.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of the append-only promotion-cost ledger ({@code promotion_cost}, TASK-BE-592).
 *
 * <p>The platform bears coupon discounts (owner decision 2026-09-15). Commission and
 * {@code seller_net} stay on the pre-discount line gross in {@link CommissionAccrual}; the
 * discount itself is recorded here, once per order — it is not split per seller. A
 * {@link PromotionCostType#COST} row is positive; a {@link PromotionCostType#REVERSAL} row is a
 * negative portion of its {@code COST} row, linked by {@code reversesCostId}. Immutable (F3).
 *
 * <p>Per order: {@code Σ commission_accrual.gross − Σ promotion_cost.amount = captured amount}.
 */
public record PromotionCost(
        String costId,
        String tenantId,
        String orderId,
        String paymentId,
        String couponId,
        PromotionCostType type,
        long amountMinor,
        String reversesCostId,
        Instant occurredAt) {

    public PromotionCost {
        if (type == PromotionCostType.COST && (amountMinor <= 0 || reversesCostId != null)) {
            throw new IllegalStateException(
                    "COST row must be positive and unlinked: amount=" + amountMinor + ", reverses=" + reversesCostId);
        }
        if (type == PromotionCostType.REVERSAL && (amountMinor >= 0 || reversesCostId == null)) {
            throw new IllegalStateException(
                    "REVERSAL row must be negative and linked: amount=" + amountMinor + ", reverses=" + reversesCostId);
        }
    }

    /** Books the discount the platform bore for a captured payment (a fresh UUID id). */
    public static PromotionCost cost(String tenantId, String orderId, String paymentId, String couponId,
                                     long discountMinor, Instant occurredAt) {
        return new PromotionCost(UUID.randomUUID().toString(), tenantId, orderId, paymentId, couponId,
                PromotionCostType.COST, discountMinor, null, occurredAt);
    }

    /**
     * Claws back {@code reversedMinor} (a positive magnitude) of this {@code COST} row for a
     * refund, as a negative {@code REVERSAL} row linked back to it. This row is never mutated (F3).
     */
    public PromotionCost toReversal(String refundPaymentId, Instant occurredAt, long reversedMinor) {
        if (type != PromotionCostType.COST) {
            throw new IllegalStateException("Only a COST row can be reversed: " + costId);
        }
        return new PromotionCost(UUID.randomUUID().toString(), tenantId, orderId, refundPaymentId, couponId,
                PromotionCostType.REVERSAL, -reversedMinor, costId, occurredAt);
    }
}
