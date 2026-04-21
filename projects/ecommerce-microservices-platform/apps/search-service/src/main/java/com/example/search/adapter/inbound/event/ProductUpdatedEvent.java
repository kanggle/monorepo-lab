package com.example.search.adapter.inbound.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Inbound event record mirroring product-service ProductUpdated contract.
 * See specs/contracts/events/product-events.md
 */
public record ProductUpdatedEvent(
        @JsonProperty("event_id") @JsonAlias("eventId") String eventId,
        @JsonProperty("event_type") @JsonAlias("eventType") String eventType,
        @JsonProperty("occurred_at") @JsonAlias("occurredAt") String occurredAt,
        String source,
        ProductUpdatedPayload payload
) {
    public record ProductUpdatedPayload(
            String productId,
            String name,
            String description,
            long price,
            String status,
            String categoryId,
            String thumbnailUrl
    ) {}
}
