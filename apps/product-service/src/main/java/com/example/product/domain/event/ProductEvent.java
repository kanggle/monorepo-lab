package com.example.product.domain.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record ProductEvent(
        @JsonProperty("event_id") UUID eventId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("occurred_at") Instant occurredAt,
        String source,
        EventPayload payload
) {
    public static ProductEvent created(ProductCreatedPayload payload) {
        return of("ProductCreated", payload);
    }

    public static ProductEvent updated(ProductUpdatedPayload payload) {
        return of("ProductUpdated", payload);
    }

    public static ProductEvent deleted(ProductDeletedPayload payload) {
        return of("ProductDeleted", payload);
    }

    public static ProductEvent stockChanged(StockChangedPayload payload) {
        return of("StockChanged", payload);
    }

    public static ProductEvent imagesUpdated(ProductImagesUpdatedPayload payload) {
        return of("ProductImagesUpdated", payload);
    }

    private static ProductEvent of(String eventType, EventPayload payload) {
        return new ProductEvent(UUID.randomUUID(), eventType, Instant.now(), "product-service", payload);
    }
}
