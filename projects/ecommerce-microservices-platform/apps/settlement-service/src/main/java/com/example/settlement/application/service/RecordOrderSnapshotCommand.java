package com.example.settlement.application.service;

import com.example.settlement.domain.model.OrderSnapshotLine;

import java.util.List;

/**
 * Command to cache an {@code OrderPlaced} line snapshot. {@code tenantId} is the
 * event-envelope tenant (settlement's authoritative tenant source, AC-7).
 * {@code discountMinor} / {@code couponId} are the order's coupon discount (TASK-BE-592;
 * {@code 0} / {@code null} without a coupon).
 */
public record RecordOrderSnapshotCommand(String orderId, String tenantId, List<OrderSnapshotLine> lines,
                                         long discountMinor, String couponId) {

    /** An order without a coupon discount. */
    public RecordOrderSnapshotCommand(String orderId, String tenantId, List<OrderSnapshotLine> lines) {
        this(orderId, tenantId, lines, 0L, null);
    }
}
