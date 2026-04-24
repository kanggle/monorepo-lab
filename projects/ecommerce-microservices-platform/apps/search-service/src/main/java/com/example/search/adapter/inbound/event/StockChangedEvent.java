package com.example.search.adapter.inbound.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Inbound event record mirroring product-service StockChanged contract.
 * See specs/contracts/events/product-events.md
 */
public record StockChangedEvent(
        @JsonProperty("event_id") @JsonAlias("eventId") String eventId,
        @JsonProperty("event_type") @JsonAlias("eventType") String eventType,
        @JsonProperty("occurred_at") @JsonAlias("occurredAt") String occurredAt,
        String source,
        StockChangedPayload payload
) {
    public record StockChangedPayload(
            String productId,
            String variantId,
            int previousStock,
            int currentStock,
            int delta,
            String reason,
            String orderId
    ) {}
}
