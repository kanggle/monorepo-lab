package com.wms.inbound.adapter.in.messaging.masterref;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/**
 * Parsed envelope of a {@code master.*} event.
 *
 * <p>Mirrors the global envelope declared in
 * {@code specs/contracts/events/master-events.md} § Global Envelope. Only the
 * fields the inbound consumers read are extracted; the rest of the JSON stays
 * in {@link #payload()} as a raw {@link JsonNode}.
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
