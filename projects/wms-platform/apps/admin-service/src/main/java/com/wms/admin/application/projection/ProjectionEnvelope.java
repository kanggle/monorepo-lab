package com.wms.admin.application.projection;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/**
 * Parsed projection envelope. Carries the cross-service outer envelope plus a
 * raw {@link JsonNode} payload — projection services dispatch on
 * {@link #eventType()} and pull payload-specific fields out of {@link #payload()}.
 */
public record ProjectionEnvelope(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        String aggregateId,
        String sourceTopic,
        JsonNode payload) {
}
