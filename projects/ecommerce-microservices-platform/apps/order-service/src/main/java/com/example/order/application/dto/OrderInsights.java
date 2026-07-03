package com.example.order.application.dto;

import java.util.List;

/**
 * Four tenant-scoped top-5 rankings derived from {@code order_items}
 * (TASK-BE-469), served by {@code GET /api/admin/orders/insights}. Mirrors
 * the sibling {@link com.example.common.summary.PeriodSummary} posture on
 * {@code /summary} — returned directly by the controller.
 */
public record OrderInsights(
        List<RankedEntry> topProductsByOrderCount,
        List<RankedEntry> topProductsByRevenue,
        List<RankedEntry> topSellersByOrderCount,
        List<RankedEntry> topSellersByRevenue) {
    public record RankedEntry(String id, String label, long value) {
    }
}
