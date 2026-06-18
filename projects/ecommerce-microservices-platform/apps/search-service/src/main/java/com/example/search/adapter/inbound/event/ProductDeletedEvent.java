package com.example.search.adapter.inbound.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Inbound event record mirroring product-service ProductDeleted contract.
 * See specs/contracts/events/product-events.md
 *
 * <p>{@code tenantId} is the M5 async propagation field on the event envelope
 * (TASK-BE-357). Delete operations are keyed by productId; tenantId is present
 * for envelope completeness but the delete path does not require it.
 */
public record ProductDeletedEvent(
        @JsonProperty("event_id") @JsonAlias("eventId") String eventId,
        @JsonProperty("event_type") @JsonAlias("eventType") String eventType,
        @JsonProperty("occurred_at") @JsonAlias("occurredAt") String occurredAt,
        String source,
        @JsonProperty("tenant_id") @JsonAlias("tenantId") String tenantId,
        ProductDeletedPayload payload
) {
    public record ProductDeletedPayload(String productId) {}
}
