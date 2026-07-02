package com.example.order.presentation.dto;

import com.example.order.application.dto.AdminOrderSummaryStats;

/**
 * Response body for {@code GET /api/admin/orders/summary} (TASK-BE-468).
 * Tenant-scoped KST calendar-period-to-date order counts.
 */
public record AdminOrderSummaryResponse(
        long today,
        long week,
        long month,
        long total
) {
    public static AdminOrderSummaryResponse from(AdminOrderSummaryStats stats) {
        return new AdminOrderSummaryResponse(stats.today(), stats.week(), stats.month(), stats.total());
    }
}
