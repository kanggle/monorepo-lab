package com.wms.admin.application.projection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Parses raw Kafka record values into {@link ProjectionEnvelope}.
 *
 * <p>Throws {@link IllegalArgumentException} on malformed input — Spring
 * Kafka's {@code DefaultErrorHandler} routes those records straight to the DLT
 * (idempotency.md § 2.7).
 */
@Component
public class ProjectionEnvelopeParser {

    private final ObjectMapper objectMapper;

    public ProjectionEnvelopeParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ProjectionEnvelope parse(String rawJson, String sourceTopic) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            UUID eventId = UUID.fromString(requireText(root, "eventId"));
            String eventType = requireText(root, "eventType");
            String aggregateId = root.path("aggregateId").asText("");
            Instant occurredAt = parseInstant(root.path("occurredAt").asText());
            JsonNode payload = root.has("payload") ? root.get("payload") : objectMapper.nullNode();
            return new ProjectionEnvelope(eventId, eventType, occurredAt, aggregateId,
                    sourceTopic, payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Malformed projection envelope for topic=" + sourceTopic, e);
        }
    }

    private static String requireText(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull() || !node.isTextual()) {
            throw new IllegalArgumentException("Missing or non-text field: " + field);
        }
        return node.asText();
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Malformed occurredAt: " + value, e);
        }
    }
}
