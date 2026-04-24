package com.example.order.application.dto;

import com.example.order.domain.model.Order;

import java.time.Instant;

public record AdminOrderSummary(
        String orderId,
        String userId,
        String status,
        long totalPrice,
        int itemCount,
        String firstItemName,
        Instant createdAt
) {
    public static AdminOrderSummary from(Order order) {
        String firstItemName = order.getItems().isEmpty()
                ? null
                : order.getItems().get(0).getProductName();
        return new AdminOrderSummary(
                order.getOrderId(),
                order.getUserId(),
                order.getStatus().name(),
                order.getTotalPrice(),
                order.getItems().size(),
                firstItemName,
                order.getCreatedAt()
        );
    }
}
