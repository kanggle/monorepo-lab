package com.example.order.presentation.dto;

import java.util.List;

/**
 * Request body for {@code POST /api/internal/orders/existence} (TASK-INT-028).
 *
 * <p>Range checks live in the controller so a violation surfaces as {@code 400 INVALID_REQUEST}
 * per the contract, matching {@link ConfirmPaidStaleRequest}.
 */
public record OrderExistenceRequest(List<String> orderIds) {

    public static final int MAX_ORDER_IDS = 500;
}
