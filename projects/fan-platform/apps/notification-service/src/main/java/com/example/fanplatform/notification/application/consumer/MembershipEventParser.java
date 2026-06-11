package com.example.fanplatform.notification.application.consumer;

import com.example.fanplatform.notification.domain.notification.NotificationType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Parses a raw Kafka record value (the canonical fan-membership envelope:
 * {@code eventId / eventType / source / occurredAt / schemaVersion /
 * partitionKey / payload}) into a validated {@link MembershipEvent}.
 *
 * <p>Failure modes (architecture.md § Consume Semantics):
 * <ul>
 *   <li>unparseable JSON / missing required field → {@link MalformedEventException}
 *       (non-retryable → DLQ).</li>
 *   <li>unsupported {@code schemaVersion} → {@link UnsupportedSchemaVersionException}
 *       (non-retryable → DLQ).</li>
 * </ul>
 *
 * <p>Forward compatibility: unknown payload fields are tolerated (only the
 * required fields are read).
 */
@Component
public class MembershipEventParser {

    static final int SUPPORTED_SCHEMA_VERSION = 1;

    private final ObjectMapper objectMapper;

    public MembershipEventParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public MembershipEvent parse(String rawValue) {
        JsonNode root;
        try {
            root = objectMapper.readTree(rawValue);
        } catch (Exception e) {
            throw new MalformedEventException("Unparseable envelope JSON: " + e.getMessage());
        }
        if (root == null || root.isNull() || !root.isObject()) {
            throw new MalformedEventException("Envelope is not a JSON object");
        }

        String eventId = requireText(root, "eventId");
        String eventType = requireText(root, "eventType");

        int schemaVersion = root.path("schemaVersion").asInt(-1);
        if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw new UnsupportedSchemaVersionException(schemaVersion, eventType);
        }

        JsonNode payload = root.get("payload");
        if (payload == null || payload.isNull() || !payload.isObject()) {
            throw new MalformedEventException("Missing payload for event " + eventType);
        }

        String tenantId = requireText(payload, "tenantId");
        String accountId = requireText(payload, "accountId");
        String membershipId = requireText(payload, "membershipId");
        String tier = requireText(payload, "tier");

        Integer planMonths = null;
        Instant validFrom = null;
        Instant validTo = null;
        String reason = null;
        Instant canceledAt = null;

        switch (eventType) {
            case NotificationType.EVENT_ACTIVATED -> {
                planMonths = requireInt(payload, "planMonths");
                validFrom = requireInstant(payload, "validFrom");
                validTo = requireInstant(payload, "validTo");
            }
            case NotificationType.EVENT_CANCELED -> {
                reason = optionalText(payload, "reason");
                canceledAt = requireInstant(payload, "canceledAt");
            }
            case NotificationType.EVENT_EXPIRED -> validTo = requireInstant(payload, "validTo");
            default -> throw new MalformedEventException("Unsupported eventType: " + eventType);
        }

        return new MembershipEvent(eventId, eventType, tenantId, accountId, membershipId,
                tier, planMonths, validFrom, validTo, reason, canceledAt);
    }

    private static String requireText(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull() || !node.isTextual() || node.asText().isBlank()) {
            throw new MalformedEventException("Missing required field: " + field);
        }
        return node.asText();
    }

    private static int requireInt(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull() || !node.isInt()) {
            throw new MalformedEventException("Missing or non-integer field: " + field);
        }
        return node.asInt();
    }

    private static String optionalText(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull() || !node.isTextual()) {
            return null;
        }
        String value = node.asText();
        return value.isBlank() ? null : value;
    }

    private static Instant requireInstant(JsonNode parent, String field) {
        String raw = requireText(parent, field);
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException e) {
            throw new MalformedEventException("Malformed timestamp in field " + field + ": " + raw);
        }
    }
}
