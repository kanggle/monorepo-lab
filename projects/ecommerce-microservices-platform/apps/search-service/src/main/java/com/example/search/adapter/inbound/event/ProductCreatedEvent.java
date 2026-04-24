package com.example.search.adapter.inbound.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Inbound event record mirroring product-service ProductCreated contract.
 * See specs/contracts/events/product-events.md
 */
public record ProductCreatedEvent(
        @JsonProperty("event_id") @JsonAlias("eventId") String eventId,
        @JsonProperty("event_type") @JsonAlias("eventType") String eventType,
        @JsonProperty("occurred_at") @JsonAlias("occurredAt") String occurredAt,
        String source,
        ProductCreatedPayload payload
) {
    public record ProductCreatedPayload(
            String productId,
            String name,
            String description,
            long price,
            String status,
            String categoryId,
            String thumbnailUrl,
            List<VariantPayload> variants
    ) {}

    public record VariantPayload(
            String variantId,
            String optionName,
            int stock,
            long additionalPrice
    ) {}
}
