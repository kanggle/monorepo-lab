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
 *
 * <p>{@code tenantId} is an <strong>additive, optional</strong> envelope-level
 * field (ADR-MONO-022 facet d, TASK-MONO-296): the cross-project
 * {@code ecommerce.fulfillment.requested.v1} event carries the originating
 * ecommerce tenant on the envelope. wms captures it as an <em>opaque
 * correlation</em> value (never interpreted/filtered — wms stays single-tenant)
 * and echoes it back on the return-leg events. {@code null} for wms-internal
 * events and for standalone/pre-M5 producers.
 */
public record EventEnvelope(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID aggregateId,
        String aggregateType,
        String tenantId,
        JsonNode payload
) {
}
