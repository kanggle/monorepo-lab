package com.example.order.infrastructure.event;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Inbound event record mirroring shipping-service ShippingStatusChanged contract.
 * Ecommerce-internal envelope (snake-ish {@code event_id}/{@code event_type}).
 * See specs/contracts/events/shipping-events.md
 */
public record ShippingStatusChangedEvent(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("occurred_at") String occurredAt,
        String source,
        ShippingStatusChangedPayload payload
) {
    public record ShippingStatusChangedPayload(
            String shippingId,
            String orderId,
            String userId,
            String previousStatus,
            String newStatus,
            String trackingNumber,
            String carrier,
            String changedAt
    ) {}
}
