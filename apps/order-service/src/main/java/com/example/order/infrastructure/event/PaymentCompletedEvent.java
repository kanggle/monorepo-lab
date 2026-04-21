package com.example.order.infrastructure.event;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Inbound event record mirroring payment-service PaymentCompleted contract.
 * See specs/contracts/events/payment-events.md
 */
public record PaymentCompletedEvent(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("occurred_at") String occurredAt,
        String source,
        PaymentCompletedPayload payload
) {
    public record PaymentCompletedPayload(
            String paymentId,
            String orderId,
            String userId,
            long amount,
            String paidAt
    ) {}
}
