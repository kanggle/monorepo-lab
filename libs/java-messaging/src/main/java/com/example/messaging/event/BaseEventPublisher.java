package com.example.messaging.event;

import com.example.common.id.UuidV7;
import com.example.messaging.outbox.OutboxWriter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared base for all service-level event publishers.
 *
 * <p>Provides two persistence paths:
 * <ul>
 *   <li>{@link #writeEvent} — builds a standard envelope (eventId, eventType, source,
 *       occurredAt, schemaVersion, partitionKey, payload) and persists to the outbox.
 *       Used by auth, community, membership, and security publishers.</li>
 *   <li>{@link #saveEvent} — serializes the given payload object as-is (no envelope).
 *       Used by account and admin publishers whose payload format differs.</li>
 * </ul>
 */
@Slf4j
public abstract class BaseEventPublisher {

    protected final OutboxWriter outboxWriter;
    protected final ObjectMapper objectMapper;

    protected BaseEventPublisher(OutboxWriter outboxWriter, ObjectMapper objectMapper) {
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
    }

    /**
     * Wraps {@code payload} in the standard event envelope and persists to the outbox.
     *
     * <p>Envelope shape:
     * <pre>
     * {
     *   "eventId":       UUID (random),
     *   "eventType":     eventType,
     *   "source":        source,
     *   "occurredAt":    ISO-8601 UTC,
     *   "schemaVersion": 1,
     *   "partitionKey":  aggregateId,
     *   "payload":       payload
     * }
     * </pre>
     */
    protected void writeEvent(String aggregateType, String aggregateId,
                              String eventType, String source,
                              Map<String, Object> payload) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", UuidV7.randomString());
        envelope.put("eventType", eventType);
        envelope.put("source", source);
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("schemaVersion", 1);
        envelope.put("partitionKey", aggregateId);
        envelope.put("payload", payload);

        outboxWriter.save(aggregateType, aggregateId, eventType, toJson(envelope));
    }

    /**
     * Serializes {@code payload} directly (no envelope) and persists to the outbox.
     */
    protected void saveEvent(String aggregateType, String aggregateId,
                             String eventType, Object payload) {
        outboxWriter.save(aggregateType, aggregateId, eventType, toJson(payload));
    }

    protected String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event payload: {}", e.getMessage());
            throw new EventSerializationException("Failed to serialize event payload", e);
        }
    }
}
