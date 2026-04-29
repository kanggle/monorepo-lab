package com.wms.outbound.adapter.in.messaging.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/**
 * Parsed envelope of a {@code master.*} event.
 *
 * <p>Mirrors the global envelope declared in
 * {@code specs/contracts/events/master-events.md} § Global Envelope.
 */
public record MasterEventEnvelope(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID aggregateId,
        String aggregateType,
        JsonNode payload
) {
}
