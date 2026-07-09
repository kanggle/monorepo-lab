package com.wms.inventory.adapter.in.messaging.settings;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Parses raw {@code wms.admin.settings.v1} Kafka payloads into a typed
 * {@link SettingsEventEnvelope}.
 *
 * <p>Failures throw {@link IllegalArgumentException} so the shared consumer
 * error handler ({@code KafkaConsumerConfig}) treats the record as
 * non-retryable and routes it to the DLT after the configured retries.
 */
@Component
public class SettingsEventParser {

    private final ObjectMapper objectMapper;

    public SettingsEventParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SettingsEventEnvelope parse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            UUID eventId = UUID.fromString(requireText(root, "eventId"));
            String eventType = requireText(root, "eventType");
            Instant occurredAt = Instant.parse(requireText(root, "occurredAt"));
            JsonNode payload = root.get("payload");
            if (payload == null || payload.isNull()) {
                throw new IllegalArgumentException("settings event missing payload: " + eventType);
            }
            return new SettingsEventEnvelope(eventId, eventType, occurredAt, payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("malformed admin.settings event JSON", e);
        }
    }

    private static String requireText(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull() || !node.isTextual()) {
            throw new IllegalArgumentException("settings event missing required field: " + field);
        }
        return node.asText();
    }
}
