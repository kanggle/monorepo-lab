package com.example.shipping.infrastructure.event;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Inbound event record mirroring order-service OrderConfirmed contract.
 * See specs/contracts/events/order-events.md
 */
public record OrderConfirmedEvent(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("occurred_at") String occurredAt,
        String source,
        OrderConfirmedPayload payload
) {
    public record OrderConfirmedPayload(
            String orderId,
            String userId,
            String confirmedAt
    ) {}
}
