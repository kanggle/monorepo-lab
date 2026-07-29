package com.example.fanplatform.notification.application.consumer;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * JSON field accessors shared by {@link MembershipEventParser} and
 * {@link CommunityEventParser} (TASK-FAN-BE-035). Every failure throws
 * {@link MalformedEventException} with the exact message strings the two
 * parsers' unit tests assert on — do not reword.
 */
final class JsonFields {

    private JsonFields() {
    }

    static String requireText(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull() || !node.isTextual() || node.asText().isBlank()) {
            throw new MalformedEventException("Missing required field: " + field);
        }
        return node.asText();
    }

    static int requireInt(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull() || !node.isInt()) {
            throw new MalformedEventException("Missing or non-integer field: " + field);
        }
        return node.asInt();
    }

    static String optionalText(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull() || !node.isTextual()) {
            return null;
        }
        String value = node.asText();
        return value.isBlank() ? null : value;
    }

    /**
     * Reads an optional array-of-strings field. An absent / null / non-array node
     * yields an empty list (rollout tolerance); a present array contributes its
     * non-blank textual entries.
     */
    static List<String> optionalTextArray(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull() || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>(node.size());
        for (JsonNode item : node) {
            if (item != null && item.isTextual() && !item.asText().isBlank()) {
                values.add(item.asText());
            }
        }
        return List.copyOf(values);
    }

    static Instant requireInstant(JsonNode parent, String field) {
        String raw = requireText(parent, field);
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException e) {
            throw new MalformedEventException("Malformed timestamp in field " + field + ": " + raw);
        }
    }
}
