package com.example.order.application.dto;

import java.util.List;

public record PlaceOrderCommand(
        String userId,
        List<OrderItemCommand> items,
        ShippingAddressCommand shippingAddress,
        // Client-supplied placement idempotency key (TASK-BE-430). Null = key-less
        // placement (non-idempotent, backward compatible).
        String idempotencyKey,
        // Coupon to apply at placement (TASK-INT-026). Null = no coupon. The discount amount
        // comes from promotion-service, never from the client.
        String couponId
) {
    /** Backward-compatible (no idempotency key, no coupon) — key-less, non-idempotent placement. */
    public PlaceOrderCommand(String userId, List<OrderItemCommand> items,
                             ShippingAddressCommand shippingAddress) {
        this(userId, items, shippingAddress, null, null);
    }

    /** Backward-compatible (no coupon). */
    public PlaceOrderCommand(String userId, List<OrderItemCommand> items,
                             ShippingAddressCommand shippingAddress, String idempotencyKey) {
        this(userId, items, shippingAddress, idempotencyKey, null);
    }

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
