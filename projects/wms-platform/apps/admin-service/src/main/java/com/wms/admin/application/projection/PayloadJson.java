package com.wms.admin.application.projection;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * Static helpers for pulling required / optional fields out of a JSON payload
 * tree. Throws {@link IllegalArgumentException} on malformed input — callers
 * propagate so Spring Kafka routes the record to DLT.
 */
public final class PayloadJson {

    private PayloadJson() {}

    public static JsonNode requireObject(JsonNode payload, String field) {
        if (payload == null || !payload.has(field) || !payload.get(field).isObject()) {
            throw new IllegalArgumentException("Missing or non-object payload field: " + field);
        }
        return payload.get(field);
    }

    public static JsonNode requireArray(JsonNode payload, String field) {
        if (payload == null || !payload.has(field) || !payload.get(field).isArray()) {
            throw new IllegalArgumentException("Missing or non-array payload field: " + field);
        }
        return payload.get(field);
    }

    public static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || !v.isTextual()) {
            throw new IllegalArgumentException("Missing or non-text field: " + field);
        }
        return v.asText();
    }

    public static String optionalText(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;
        return v.asText();
    }

    public static UUID uuid(JsonNode node, String field) {
        return UUID.fromString(text(node, field));
    }

    public static UUID optionalUuid(JsonNode node, String field) {
        String t = optionalText(node, field);
        return t == null || t.isBlank() ? null : UUID.fromString(t);
    }

    public static int integer(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || !v.isNumber()) {
            throw new IllegalArgumentException("Missing or non-numeric field: " + field);
        }
        return v.asInt();
    }

    public static int optionalInteger(JsonNode node, String field, int defaultValue) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || !v.isNumber()) return defaultValue;
        return v.asInt();
    }

    public static Integer optionalIntegerBoxed(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || !v.isNumber()) return null;
        return v.asInt();
    }

    public static LocalDate optionalDate(JsonNode node, String field) {
        String t = optionalText(node, field);
        if (t == null || t.isBlank()) return null;
        try {
            return LocalDate.parse(t);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Malformed date for field " + field + ": " + t, e);
        }
    }

    public static Instant optionalInstant(JsonNode node, String field) {
        String t = optionalText(node, field);
        if (t == null || t.isBlank()) return null;
        try {
            return Instant.parse(t);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Malformed instant for field " + field + ": " + t, e);
        }
    }
}
