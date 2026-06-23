package com.example.order.presentation.dto;

/**
 * Request body for {@code POST /api/internal/orders/{orderId}/cancel} (TASK-BE-428).
 *
 * <p>{@code reason} is an optional free-text operator note (e.g. {@code "backorder never
 * replenished"}); it is logged for audit and is not required. A missing body resolves to
 * a {@code null} reason.
 */
public record OperatorCancelOrderRequest(String reason) {
}
