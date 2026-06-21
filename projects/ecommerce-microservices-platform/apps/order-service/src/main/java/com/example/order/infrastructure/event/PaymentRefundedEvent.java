package com.example.order.infrastructure.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Inbound event record mirroring payment-service PaymentRefunded contract.
 * See specs/contracts/events/payment-events.md
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentRefundedEvent(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("occurred_at") String occurredAt,
        String source,
        PaymentRefundedPayload payload
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentRefundedPayload(
            String paymentId,
            String orderId,
            String userId,
            long amount,
            long totalRefunded,
            /** Nullable for back-compat: a legacy event without this field is a full refund. */
            Boolean fullyRefunded,
            String refundedAt
    ) {}
}
