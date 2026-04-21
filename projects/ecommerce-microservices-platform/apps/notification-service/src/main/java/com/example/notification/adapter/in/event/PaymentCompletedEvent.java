package com.example.notification.adapter.in.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record PaymentCompletedEvent(
        @JsonProperty("event_id") @JsonAlias("eventId") String eventId,
        @JsonProperty("event_type") @JsonAlias("eventType") String eventType,
        @JsonProperty("occurred_at") @JsonAlias("occurredAt") String occurredAt,
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
