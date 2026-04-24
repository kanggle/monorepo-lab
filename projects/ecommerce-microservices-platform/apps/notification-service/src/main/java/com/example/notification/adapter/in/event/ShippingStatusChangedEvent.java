package com.example.notification.adapter.in.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record ShippingStatusChangedEvent(
        @JsonProperty("event_id") @JsonAlias("eventId") String eventId,
        @JsonProperty("event_type") @JsonAlias("eventType") String eventType,
        @JsonProperty("occurred_at") @JsonAlias("occurredAt") String occurredAt,
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
