package com.example.order.application.dto;

import java.util.List;

public record PlaceOrderCommand(
        String userId,
        List<OrderItemCommand> items,
        ShippingAddressCommand shippingAddress
) {
    public record OrderItemCommand(
            String productId,
            String variantId,
            String productName,
            String optionName,
            int quantity,
            long unitPrice,
            String sellerId
    ) {
        /** Backward-compatible (no seller) — defaults to the default seller (D8). */
        public OrderItemCommand(String productId, String variantId, String productName,
                                String optionName, int quantity, long unitPrice) {
            this(productId, variantId, productName, optionName, quantity, unitPrice, null);
        }
    }

    public record ShippingAddressCommand(
            String recipient,
            String phone,
            String zipCode,
            String address1,
            String address2
    ) {}
}
