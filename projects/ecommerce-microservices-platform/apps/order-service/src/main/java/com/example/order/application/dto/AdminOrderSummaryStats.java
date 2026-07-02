package com.example.order.application.dto;

/**
 * Tenant-scoped KST calendar-period-to-date order counts returned by
 * {@link com.example.order.application.service.OrderQueryService#getOrderSummary()}
 * (TASK-BE-468).
 */
public record AdminOrderSummaryStats(
        long today,
        long week,
        long month,
        long total
) {}
