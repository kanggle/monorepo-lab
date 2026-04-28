package com.wms.inventory.adapter.in.messaging.outbound;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Lightweight envelope parser shared by the three outbound consumers
 * (picking.requested / picking.cancelled / shipping.confirmed).
 *
 * <p>Returns the parsed eventId + eventType + raw payload {@link JsonNode};
 * each consumer pulls the per-event fields from the payload directly.
 */
@Component
public class OutboundEventParser {

    private final ObjectMapper objectMapper;

    public OutboundEventParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Parsed parse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            UUID eventId = UUID.fromString(requireText(root, "eventId"));
            String eventType = requireText(root, "eventType");
            JsonNode payload = root.get("payload");
            if (payload == null || payload.isNull()) {
                throw new IllegalArgumentException(
                        "Outbound event missing payload: " + eventType);
            }
            return new Parsed(eventId, eventType, payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Malformed outbound event JSON", e);
        }
    }

    private static String requireText(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull() || !node.isTextual()) {
            throw new IllegalArgumentException("Missing or non-text field: " + field);
        }
        return node.asText();
    }

    public record Parsed(UUID eventId, String eventType, JsonNode payload) {
    }
}
