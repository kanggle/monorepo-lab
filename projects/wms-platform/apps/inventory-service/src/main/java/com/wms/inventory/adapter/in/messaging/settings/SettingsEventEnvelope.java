package com.wms.inventory.adapter.in.messaging.settings;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/**
 * Parsed view of an {@code admin.settings.changed} event
 * ({@code wms.admin.settings.v1}, published by admin-service — see
 * {@code specs/contracts/events/admin-events.md} §10).
 *
 * <p>Distinct from {@code MasterEventEnvelope}: settings events carry the
 * setting {@code key} (a string) as {@code aggregateId}, not a UUID, so the
 * master-ref parser cannot be reused. The {@code payload} carries
 * {@code {key, scope, warehouseId, valueJson, previousValueJson, version}}.
 */
public record SettingsEventEnvelope(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        JsonNode payload) {
}
