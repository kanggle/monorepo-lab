package com.example.order.domain.repository;

/**
 * One product's tenant-scoped aggregation row from {@code order_items},
 * excluding CANCELLED orders (TASK-BE-469). {@code orderCount} is the number
 * of distinct orders containing the product; {@code revenue} is
 * {@code SUM(unit_price * quantity)} across the product's lines.
 *
 * <p>A repository projection (a port return type), so it lives in the domain
 * layer alongside {@link OrderRepository} — not in {@code application.dto}.
 */
public record ProductOrderRankingRow(String productId, String productName, long orderCount, long revenue) {
}
