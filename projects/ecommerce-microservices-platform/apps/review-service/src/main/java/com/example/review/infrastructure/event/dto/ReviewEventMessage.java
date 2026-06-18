package com.example.review.infrastructure.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Infrastructure DTO for Kafka event serialization.
 * Maps domain ReviewEvent fields to contract-defined JSON field names.
 * Domain layer must NOT depend on this class.
 *
 * <p>TASK-BE-403 (ADR-MONO-030 Step 4 facet c): {@code tenant_id} added as a
 * top-level envelope field alongside the existing standard fields. Additive —
 * existing consumers that do not read {@code tenant_id} are unaffected.
 */
public record ReviewEventMessage(
        @JsonProperty("event_id") UUID eventId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("occurred_at") Instant occurredAt,
        String source,
        @JsonProperty("tenant_id") String tenantId,
        Object payload
) {
}
