package com.example.payment.adapter.in.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Inbound event record mirroring order-service OrderPlaced contract.
 * See specs/contracts/events/order-events.md
 */
public record OrderPlacedEvent(
        @JsonProperty("event_id") @JsonAlias("eventId") String eventId,
        @JsonProperty("event_type") @JsonAlias("eventType") String eventType,
        @JsonProperty("occurred_at") @JsonAlias("occurredAt") String occurredAt,
        String source,
        OrderPlacedPayload payload
) {
    public record OrderPlacedPayload(
            String orderId,
            String userId,
            long totalPrice,
            List<OrderItem> items,
            ShippingAddress shippingAddress
    ) {}

    public record OrderItem(
            String productId,
            String variantId,
            int quantity,
            long unitPrice
    ) {}

    public record ShippingAddress(
            String recipient,
            String phone,
            String zipCode,
            String address1,
            String address2
    ) {}
}
