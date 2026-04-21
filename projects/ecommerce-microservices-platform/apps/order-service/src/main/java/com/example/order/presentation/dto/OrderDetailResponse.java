package com.example.order.presentation.dto;

import com.example.order.application.dto.OrderDetail;

import java.time.Instant;
import java.util.List;

public record OrderDetailResponse(
        String orderId,
        String status,
        long totalPrice,
        List<OrderItemDetail> items,
        ShippingAddressDetail shippingAddress,
        Instant createdAt,
        Instant updatedAt
) {
    public static OrderDetailResponse from(OrderDetail detail) {
        List<OrderItemDetail> items = detail.items().stream()
                .map(i -> new OrderItemDetail(
                        i.productId(), i.variantId(), i.productName(),
                        i.optionName(), i.quantity(), i.unitPrice()
                ))
                .toList();

        OrderDetail.ShippingAddressDetail addr = detail.shippingAddress();
        ShippingAddressDetail addrItem = new ShippingAddressDetail(
                addr.recipient(), addr.phone(), addr.zipCode(), addr.address1(), addr.address2()
        );

        return new OrderDetailResponse(
                detail.orderId(), detail.status(), detail.totalPrice(),
                items, addrItem, detail.createdAt(), detail.updatedAt()
        );
    }

    public record OrderItemDetail(
            String productId,
            String variantId,
            String productName,
            String optionName,
            int quantity,
            long unitPrice
    ) {}

    public record ShippingAddressDetail(
            String recipient,
            String phone,
            String zipCode,
            String address1,
            String address2
    ) {}
}
