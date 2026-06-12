package com.example.order.application.dto;

import com.example.order.domain.model.Order;
import com.example.order.domain.model.ShippingAddress;

import java.time.Instant;
import java.util.List;

public record OrderDetail(
        String orderId,
        String status,
        long totalPrice,
        List<OrderItemDetail> items,
        ShippingAddressDetail shippingAddress,
        Instant createdAt,
        Instant updatedAt
) {
    public static OrderDetail from(Order order) {
        return new OrderDetail(
                order.getOrderId(),
                order.getStatus().name(),
                order.getTotalPrice(),
                mapItems(order),
                mapAddress(order.getShippingAddress()),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    public static List<OrderItemDetail> mapItems(Order order) {
        return order.getItems().stream()
                .map(i -> new OrderItemDetail(
                        i.getProductId(), i.getVariantId(),
                        i.getProductName(), i.getOptionName(),
                        i.getQuantity(), i.getUnitPrice(), i.getSellerId()
                ))
                .toList();
    }

    public static ShippingAddressDetail mapAddress(ShippingAddress addr) {
        return new ShippingAddressDetail(
                addr.getRecipient(), addr.getPhone(), addr.getZipCode(),
                addr.getAddress1(), addr.getAddress2()
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
