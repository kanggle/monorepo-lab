package com.wms.outbound.adapter.in.messaging.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/**
 * Parsed envelope of an inventory-service event consumed by
 * outbound-service saga consumers. {@code payload} stays as a
 * {@link JsonNode} so each consumer pulls the fields it needs.
 */
public record InventoryEventEnvelope(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID aggregateId,
        String aggregateType,
        JsonNode payload
) {
}
