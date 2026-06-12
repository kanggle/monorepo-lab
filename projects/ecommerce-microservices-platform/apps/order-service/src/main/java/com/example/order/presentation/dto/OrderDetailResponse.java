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
                        i.optionName(), i.quantity(), i.unitPrice(), i.sellerId()
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
            long unitPrice,
            String sellerId
    ) {
        /** Backward-compatible (no seller) — defaults to the default seller (D8). */
        public OrderItemDetail(String productId, String variantId, String productName,
                               String optionName, int quantity, long unitPrice) {
            this(productId, variantId, productName, optionName, quantity, unitPrice, "default");
        }
    }

    public record ShippingAddressDetail(
            String recipient,
            String phone,
            String zipCode,
            String address1,
            String address2
    ) {}
}
