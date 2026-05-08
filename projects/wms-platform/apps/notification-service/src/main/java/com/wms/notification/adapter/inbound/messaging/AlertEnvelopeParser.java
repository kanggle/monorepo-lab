package com.wms.notification.adapter.inbound.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.notification.domain.alert.AlertEnvelope;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Parses inbound JSON event payloads into the canonical
 * {@link AlertEnvelope}. Failure to parse → {@link IllegalArgumentException}
 * → DLT routing (handled by {@code KafkaConsumerConfig}).
 */
@Component
public class AlertEnvelopeParser {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public AlertEnvelopeParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Parse a producer-shaped envelope. Producer services emit envelopes in
     * the shape {@code {eventId, eventType, occurredAt, aggregateId,
     * payload}} — see {@code specs/contracts/events/<service>-events.md}.
     *
     * @param rawJson    raw Kafka record value
     * @param sourceTopic Kafka topic name (passed through to the envelope)
     */
    public AlertEnvelope parse(String rawJson, String sourceTopic) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            UUID eventId = UUID.fromString(requireText(root, "eventId"));
            String eventType = requireText(root, "eventType");
            String aggregateId = root.path("aggregateId").asText("");
            Instant occurredAt = parseInstant(root.path("occurredAt").asText());
            JsonNode payloadNode = root.get("payload");
            Map<String, Object> payload;
            if (payloadNode == null || payloadNode.isNull()) {
                payload = new HashMap<>();
            } else {
                payload = objectMapper.convertValue(payloadNode, MAP_TYPE);
            }
            return new AlertEnvelope(eventId, eventType, sourceTopic, aggregateId, payload, occurredAt);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Malformed inbound envelope for topic=" + sourceTopic, e);
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
