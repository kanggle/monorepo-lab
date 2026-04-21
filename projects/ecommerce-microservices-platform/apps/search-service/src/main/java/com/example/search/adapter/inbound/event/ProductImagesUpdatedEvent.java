package com.example.search.adapter.inbound.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Inbound event record mirroring product-service ProductImagesUpdated contract.
 * See specs/contracts/events/product-events.md
 */
public record ProductImagesUpdatedEvent(
        @JsonProperty("event_id") @JsonAlias("eventId") String eventId,
        @JsonProperty("event_type") @JsonAlias("eventType") String eventType,
        @JsonProperty("occurred_at") @JsonAlias("occurredAt") String occurredAt,
        String source,
        ProductImagesUpdatedPayload payload
) {
    public record ProductImagesUpdatedPayload(
            String productId,
            String thumbnailUrl,
            List<ImageSnapshot> images
    ) {}

    public record ImageSnapshot(
            String imageId,
            String objectKey,
            String url,
            int sortOrder,
            boolean isPrimary
    ) {}
}
