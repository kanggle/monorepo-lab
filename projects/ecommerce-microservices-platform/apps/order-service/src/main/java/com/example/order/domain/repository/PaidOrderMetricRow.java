package com.example.order.domain.repository;

import java.time.Instant;

/**
 * One realised-sale order's contribution to the overview performance metrics
 * (ADR-MONO-084, TASK-MONO-807) — the repository returns one row per order paid inside
 * the window, and the application layer buckets them by KST date.
 *
 * <p>A repository projection (a port return type), so it lives in the domain layer
 * alongside {@link OrderRepository}, like {@link ProductOrderRankingRow}.
 *
 * @param paidAt  when the order was paid — the instant the sale is attributed to
 * @param revenue unrestricted view: the order's {@code total_price} (what the customer paid,
 *                net of any coupon); seller-scoped view: the sum of that seller's lines
 *                ({@code unit_price × quantity}, before the order-level coupon, which is not
 *                allocated to sellers)
 * @param units   the quantity on the lines in view (all lines, or the seller's lines)
 */
public record PaidOrderMetricRow(Instant paidAt, long revenue, long units) {
}
