package com.example.order.application.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public record OrderSagaRecoveryExhaustedEvent(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("occurred_at") String occurredAt,
        String source,
        Payload payload
) {
    public static OrderSagaRecoveryExhaustedEvent of(String orderId, String userId,
                                                     String lastState, int attemptCount,
                                                     Instant placedAt, Instant lastTransitionAt,
                                                     String failureReason, Clock clock) {
        return new OrderSagaRecoveryExhaustedEvent(
                UUID.randomUUID().toString(),
                "OrderSagaRecoveryExhausted",
                Instant.now(clock).toString(),
                "order-service",
                new Payload(orderId, userId, lastState, attemptCount,
                        placedAt.toString(), lastTransitionAt.toString(), failureReason)
        );
    }

    public record Payload(
            String orderId,
            String userId,
            String lastState,
            int attemptCount,
            String placedAt,
            String lastTransitionAt,
            String failureReason
    ) {}
}
