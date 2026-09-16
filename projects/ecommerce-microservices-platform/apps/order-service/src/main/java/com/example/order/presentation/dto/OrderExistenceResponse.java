package com.example.order.presentation.dto;

import java.util.List;

/**
 * Response body for {@code POST /api/internal/orders/existence} (TASK-INT-028): the subset of the
 * requested ids that exist in any tenant. Ids that were not requested are never included.
 */
public record OrderExistenceResponse(List<String> existingOrderIds) {
}
