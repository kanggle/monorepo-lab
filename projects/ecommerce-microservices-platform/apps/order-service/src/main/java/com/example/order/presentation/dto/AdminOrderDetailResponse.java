package com.example.order.presentation.dto;

import com.example.order.application.dto.AdminOrderDetail;
import com.example.order.application.dto.OrderDetail;

import java.time.Instant;
import java.util.List;

public record AdminOrderDetailResponse(
        String orderId,
        String userId,
        String status,
        long totalPrice,
        List<OrderDetailResponse.OrderItemDetail> items,
        OrderDetailResponse.ShippingAddressDetail shippingAddress,
        Instant createdAt,
        Instant updatedAt
) {
    public static AdminOrderDetailResponse from(AdminOrderDetail detail) {
        List<OrderDetailResponse.OrderItemDetail> items = detail.items().stream()
                .map(i -> new OrderDetailResponse.OrderItemDetail(
                        i.productId(), i.variantId(), i.productName(),
                        i.optionName(), i.quantity(), i.unitPrice(), i.sellerId()
                ))
                .toList();

        OrderDetail.ShippingAddressDetail addr = detail.shippingAddress();
        OrderDetailResponse.ShippingAddressDetail addrItem = new OrderDetailResponse.ShippingAddressDetail(
                addr.recipient(), addr.phone(), addr.zipCode(), addr.address1(), addr.address2()
        );

        return new AdminOrderDetailResponse(
                detail.orderId(), detail.userId(), detail.status(), detail.totalPrice(),
                items, addrItem, detail.createdAt(), detail.updatedAt()
        );
    }
}
