package com.example.messaging.envelope;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * Parses raw Kafka record values into the canonical {@link EventEnvelope}.
 *
 * <p>Throws {@link IllegalArgumentException} on malformed input — Spring Kafka's
 * {@code DefaultErrorHandler} treats this as non-retryable and routes the record
 * to the dead-letter topic. Parser is registered as a Spring {@code @Component}
 * so consumers can inject it directly; constructor takes an {@link ObjectMapper}
 * to align with whichever Jackson configuration the service uses.
 *
 * <p>Field tolerance:
 * <ul>
 *   <li>{@code eventId}, {@code eventType}, {@code occurredAt}, {@code aggregateType},
 *       {@code aggregateId} — required; missing or non-text values throw.</li>
 *   <li>{@code eventVersion} — defaults to {@code 1} when absent.</li>
 *   <li>{@code producer}, {@code traceId}, {@code actorId} — nullable.</li>
 *   <li>{@code payload} — required (an event with no payload is invalid).</li>
 * </ul>
 */
@Component
public class EventEnvelopeParser {

    private final ObjectMapper objectMapper;

    public EventEnvelopeParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Parse the JSON envelope.
     *
     * @param json the raw Kafka record value
     * @return parsed envelope
     * @throws IllegalArgumentException if the envelope is malformed
     */
    public EventEnvelope parse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            UUID eventId = UUID.fromString(requireText(root, "eventId"));
            String eventType = requireText(root, "eventType");
            int eventVersion = root.path("eventVersion").asInt(1);
            Instant occurredAt = parseInstant(requireText(root, "occurredAt"));
            String producer = optionalText(root, "producer");
            String aggregateType = requireText(root, "aggregateType");
            String aggregateId = requireText(root, "aggregateId");
            String traceId = optionalText(root, "traceId");
            String actorId = optionalText(root, "actorId");
            JsonNode payload = root.get("payload");
            if (payload == null || payload.isNull()) {
                throw new IllegalArgumentException("event envelope missing payload: " + eventType);
            }
            return new EventEnvelope(
                    eventId, eventType, eventVersion, occurredAt, producer,
                    aggregateType, aggregateId, traceId, actorId, payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("malformed event envelope JSON", e);
        }
    }

    private static String requireText(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull() || !node.isTextual()) {
            throw new IllegalArgumentException("event envelope missing required field: " + field);
        }
        return node.asText();
    }

    private static String optionalText(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        return node.isTextual() ? node.asText() : null;
    }

    private static Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("malformed envelope timestamp: " + value, e);
        }
    }
}
