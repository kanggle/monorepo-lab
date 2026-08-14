package com.wms.admin.application.projection;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/**
 * Parsed projection envelope. Carries the cross-service outer envelope plus a
 * raw {@link JsonNode} payload — projection services dispatch on
 * {@link #eventType()} and pull payload-specific fields out of {@link #payload()}.
 *
 * <p>{@code tenantId} is <b>envelope-level, not payload</b> (outbound-events.md
 * § Global Envelope). It is the owning customer tenant of the aggregate, and for the
 * outbound order/shipment projections it is the isolation axis those dashboards
 * filter by (ADR-MONO-065 § D1/D2). {@code null} for every event type that does not
 * carry one, and for orders that genuinely have no tenant.
 *
 * <p>🔴 The projection stores this value <b>verbatim</b>. It must not be re-derived
 * by looking the aggregate up or inferred from {@code source} — a projection that
 * reconstructs an isolation key becomes a second, divergent source of truth
 * (ADR-MONO-065 § D2).
 */
public record ProjectionEnvelope(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        String aggregateId,
        String sourceTopic,
        String tenantId,
        JsonNode payload) {
}
