package com.example.notification.adapter.in.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record OrderPlacedEvent(
        @JsonProperty("event_id") @JsonAlias("eventId") String eventId,
        @JsonProperty("event_type") @JsonAlias("eventType") String eventType,
        @JsonProperty("occurred_at") @JsonAlias("occurredAt") String occurredAt,
        String source,
        @JsonProperty("tenant_id") @JsonAlias("tenantId") String tenantId,
        OrderPlacedPayload payload
) {
    public record OrderPlacedPayload(
            String orderId,
            String userId,
            long totalPrice
    ) {}
}
