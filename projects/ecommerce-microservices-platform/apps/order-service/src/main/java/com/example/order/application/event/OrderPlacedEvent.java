package com.example.order.application.event;

import com.example.order.domain.tenant.TenantContext;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderPlacedEvent(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("occurred_at") String occurredAt,
        String source,
        @JsonProperty("tenant_id") String tenantId,
        Payload payload
) {
    /** Backward-compatible (no coupon). */
    public static OrderPlacedEvent of(String orderId, String userId, long totalPrice,
                                      List<Item> items, ShippingAddress shippingAddress,
                                      Clock clock) {
        return of(orderId, userId, totalPrice, items, shippingAddress, null, 0L, clock);
    }

    public static OrderPlacedEvent of(String orderId, String userId, long totalPrice,
                                      List<Item> items, ShippingAddress shippingAddress,
                                      String couponId, long discountAmount,
                                      Clock clock) {
        // Envelope tenant (M5): the order's owning tenant from the request context;
        // standalone/background resolves to the default tenant (net-zero, D8).
        return new OrderPlacedEvent(
                UUID.randomUUID().toString(),
                "OrderPlaced",
                Instant.now(clock).toString(),
                "order-service",
                TenantContext.currentTenant(),
                new Payload(orderId, userId, totalPrice, items, shippingAddress, couponId, discountAmount)
        );
    }

    /**
     * {@code totalPrice} is the amount the customer pays — net of {@code discountAmount}
     * (TASK-INT-026). {@code couponId} / {@code discountAmount} are additive: null / 0 when the
     * order has no coupon.
     */
    public record Payload(
            String orderId,
            String userId,
            long totalPrice,
            List<Item> items,
            ShippingAddress shippingAddress,
            String couponId,
            long discountAmount
    ) {
        /** Backward-compatible (no coupon). */
        public Payload(String orderId, String userId, long totalPrice,
                       List<Item> items, ShippingAddress shippingAddress) {
            this(orderId, userId, totalPrice, items, shippingAddress, null, 0L);
        }
    }

    public record Item(
            String productId,
            String variantId,
            int quantity,
            long unitPrice,
            String sellerId
    ) {
        /** Backward-compatible (no seller) — defaults to the default seller (D8). */
        public Item(String productId, String variantId, int quantity, long unitPrice) {
            this(productId, variantId, quantity, unitPrice, "default");
        }
    }

    public record ShippingAddress(
            String recipient,
            String phone,
            String zipCode,
            String address1,
            String address2
    ) {}
}
