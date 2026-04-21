package com.example.promotion.interfaces.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Inbound event record mirroring order-service OrderCancelled contract.
 * See specs/contracts/events/order-events.md
 */
public record OrderCancelledEvent(
        @JsonProperty("event_id") @JsonAlias("eventId") String eventId,
        @JsonProperty("event_type") @JsonAlias("eventType") String eventType,
        @JsonProperty("occurred_at") @JsonAlias("occurredAt") String occurredAt,
        String source,
        OrderCancelledPayload payload
) {
    public record OrderCancelledPayload(
            String orderId,
            String userId,
            String cancelledAt
    ) {}
}
