package com.example.settlement.domain.model;

import java.util.List;

/**
 * A cached {@code OrderPlaced} line snapshot — the settlement attribution source.
 * Keyed by {@code orderId}, it holds the order's {@code tenant_id} (from the event
 * envelope — the <b>only</b> authoritative tenant source for settlement, AC-7) and
 * the per-line {@code (seller_id, gross_minor)} pairs.
 *
 * <p>The accrual / reversal consumers join this by {@code orderId} to learn the
 * tenant and each line's seller; settlement never calls order/payment HTTP APIs to
 * backfill it (consumer rule). A missing snapshot at accrual time = unattributable
 * (F2 — the consumer raises → retry → DLQ).
 *
 * <p>{@code discountMinor} / {@code couponId} (TASK-BE-592) are the order's coupon discount from
 * the additive {@code OrderPlaced.discountAmount} / {@code couponId}. The platform bears it: it is
 * booked as a separate promotion cost at capture and sets the refund fraction's denominator
 * ({@code Σ line gross − discountMinor} = the captured amount). {@code 0} / {@code null} for an
 * order without a coupon (or an event without the fields).
 */
public record OrderSnapshot(String orderId, String tenantId, List<OrderSnapshotLine> lines,
                            long discountMinor, String couponId) {

    public OrderSnapshot {
        lines = lines == null ? List.of() : List.copyOf(lines);
        if (discountMinor < 0) {
            throw new IllegalArgumentException("discountMinor must not be negative: " + discountMinor);
        }
    }

    /** An order without a coupon discount. */
    public OrderSnapshot(String orderId, String tenantId, List<OrderSnapshotLine> lines) {
        this(orderId, tenantId, lines, 0L, null);
    }
}
