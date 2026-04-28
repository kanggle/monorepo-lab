package com.wms.inventory.adapter.in.messaging.masterref;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Parses raw Kafka payloads into a typed {@link MasterEventEnvelope}.
 *
 * <p>Failures throw {@link IllegalArgumentException} so the consumer's error
 * handler treats the record as non-retryable and routes it to the DLT after
 * the configured retries. (The Spring Kafka error handler classifies
 * {@link IllegalArgumentException} as fatal by default.)
 */
@Component
public class MasterEventParser {

    private final ObjectMapper objectMapper;

    public MasterEventParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public MasterEventEnvelope parse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            UUID eventId = UUID.fromString(requireText(root, "eventId"));
            String eventType = requireText(root, "eventType");
            Instant occurredAt = Instant.parse(requireText(root, "occurredAt"));
            UUID aggregateId = UUID.fromString(requireText(root, "aggregateId"));
            String aggregateType = requireText(root, "aggregateType");
            JsonNode payload = root.get("payload");
            if (payload == null || payload.isNull()) {
                throw new IllegalArgumentException("event missing payload: " + eventType);
            }
            return new MasterEventEnvelope(
                    eventId, eventType, occurredAt, aggregateId, aggregateType, payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("malformed master event JSON", e);
        }
    }

    private static String requireText(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull() || !node.isTextual()) {
            throw new IllegalArgumentException("event missing required field: " + field);
        }
        return node.asText();
    }
}
