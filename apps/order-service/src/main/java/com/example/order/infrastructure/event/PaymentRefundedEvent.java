package com.example.order.infrastructure.event;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Inbound event record mirroring payment-service PaymentRefunded contract.
 * See specs/contracts/events/payment-events.md
 */
public record PaymentRefundedEvent(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("occurred_at") String occurredAt,
        String source,
        PaymentRefundedPayload payload
) {
    public record PaymentRefundedPayload(
            String paymentId,
            String orderId,
            String userId,
            long amount,
            String refundedAt
    ) {}
}
