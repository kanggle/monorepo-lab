package com.wms.outbound.adapter.in.messaging.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/**
 * Parsed envelope shared by every Kafka consumer in this service.
 *
 * <p>Mirrors the global envelope declared in
 * {@code specs/contracts/events/master-events.md} § Global Envelope and the
 * inventory-event envelope per {@code specs/contracts/events/inventory-events.md}
 * — both families share the identical envelope shape, so a single record
 * suffices for {@code wms.master.*} and {@code wms.inventory.*} topics.
 *
 * <p>{@code payload} stays as a {@link JsonNode} so each consumer pulls the
 * fields it needs.
 */
public record EventEnvelope(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID aggregateId,
        String aggregateType,
        JsonNode payload
) {
}
