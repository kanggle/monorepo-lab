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
            long unitPrice
    ) {}

    public record ShippingAddressCommand(
            String recipient,
            String phone,
            String zipCode,
            String address1,
            String address2
    ) {}
}
