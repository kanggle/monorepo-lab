package com.wms.notification.domain.alert;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Canonical inbound event envelope. The 6 source-topic consumers all
 * produce one of these so the application service handles a single shape.
 *
 * <p>Plain Java — no framework deps (domain layer rule §1).
 *
 * @param eventId    UUIDv7 from the source service's envelope
 * @param eventType  source-derived event type (e.g. {@code inventory.low-stock-detected})
 * @param sourceTopic Kafka topic the event arrived on (for observability + DLT replay)
 * @param aggregateId aggregate id from the source event (for partition key)
 * @param payload    canonical payload as a parsed map; the routing matcher reads from this
 * @param occurredAt event-time from the source envelope
 */
public record AlertEnvelope(
        UUID eventId,
        String eventType,
        String sourceTopic,
        String aggregateId,
        Map<String, Object> payload,
        Instant occurredAt
) {

    public AlertEnvelope {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(sourceTopic, "sourceTopic");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
