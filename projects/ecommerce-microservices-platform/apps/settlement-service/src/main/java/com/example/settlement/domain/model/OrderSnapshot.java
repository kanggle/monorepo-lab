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
 */
public record OrderSnapshot(String orderId, String tenantId, List<OrderSnapshotLine> lines) {

    public OrderSnapshot {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
