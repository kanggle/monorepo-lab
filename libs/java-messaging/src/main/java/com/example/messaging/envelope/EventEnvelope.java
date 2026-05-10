package com.example.messaging.envelope;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/**
 * Canonical event envelope shared by every service in this monorepo.
 *
 * <p>The envelope is the on-wire shape produced by the outbox writer and consumed
 * by Kafka listeners. Domain-specific payload types live inside {@link #payload()}
 * as a {@link JsonNode}; the envelope itself stays project-agnostic so the same
 * record type works for every domain.
 *
 * <p>Field reference (matches {@code specs/contracts/events/event-envelope.schema.json}
 * across projects):
 * <ul>
 *   <li>{@code eventId}       — globally unique UUIDv7</li>
 *   <li>{@code eventType}     — dotted name (e.g. {@code <bounded-context>.<verb>})</li>
 *   <li>{@code eventVersion}  — schema version (1, 2, …)</li>
 *   <li>{@code occurredAt}    — domain timestamp (ISO-8601 UTC)</li>
 *   <li>{@code producer}      — emitting service name</li>
 *   <li>{@code aggregateType} — bounded-context entity type</li>
 *   <li>{@code aggregateId}   — entity identifier</li>
 *   <li>{@code traceId}       — distributed-trace id (nullable)</li>
 *   <li>{@code actorId}       — operator/user that triggered the event (nullable)</li>
 *   <li>{@code payload}       — domain-specific JSON</li>
 * </ul>
 *
 * <p>The envelope is intentionally a {@code record} so callers can pattern-match
 * against it; serialization is delegated to the caller's {@code ObjectMapper}.
 */
public record EventEnvelope(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String producer,
        String aggregateType,
        String aggregateId,
        String traceId,
        String actorId,
        JsonNode payload
) {
}
