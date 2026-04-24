package com.example.order.application.dto;

import com.example.order.domain.model.Order;

import java.time.Instant;
import java.util.List;

public record AdminOrderDetail(
        String orderId,
        String userId,
        String status,
        long totalPrice,
        List<OrderDetail.OrderItemDetail> items,
        OrderDetail.ShippingAddressDetail shippingAddress,
        Instant createdAt,
        Instant updatedAt
) {
    public static AdminOrderDetail from(Order order) {
        return new AdminOrderDetail(
                order.getOrderId(),
                order.getUserId(),
                order.getStatus().name(),
                order.getTotalPrice(),
                OrderDetail.mapItems(order),
                OrderDetail.mapAddress(order.getShippingAddress()),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
