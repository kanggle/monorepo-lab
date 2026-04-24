package com.example.order.application.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public record OrderCancelledEvent(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("occurred_at") String occurredAt,
        String source,
        Payload payload
) {
    public static OrderCancelledEvent of(String orderId, String userId,
                                          Instant cancelledAt, Clock clock) {
        return new OrderCancelledEvent(
                UUID.randomUUID().toString(),
                "OrderCancelled",
                Instant.now(clock).toString(),
                "order-service",
                new Payload(orderId, userId, cancelledAt.toString())
        );
    }

    public record Payload(String orderId, String userId, String cancelledAt) {}
}
