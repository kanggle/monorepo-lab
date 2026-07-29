package com.example.fanplatform.notification.application.consumer;

import com.example.fanplatform.notification.domain.notification.NotificationType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses a raw Kafka record value (the canonical community envelope:
 * {@code eventId / eventType / source / occurredAt / schemaVersion /
 * partitionKey / payload}) into a validated {@link CommunityEvent}
 * (TASK-FAN-BE-026). Mirrors {@link MembershipEventParser} and reuses its two
 * failure exceptions.
 *
 * <p>Failure modes (architecture.md § Consume Semantics):
 * <ul>
 *   <li>unparseable JSON / missing required field → {@link MalformedEventException}
 *       (non-retryable → DLQ).</li>
 *   <li>unsupported {@code schemaVersion} → {@link UnsupportedSchemaVersionException}
 *       (non-retryable → DLQ).</li>
 * </ul>
 *
 * <p><b>Rollout tolerance</b> — the recipient-routing fields added by
 * TASK-FAN-BE-026 are read <em>optionally</em>: an in-flight event emitted before
 * the producer enrichment has no {@code postAuthorAccountId} (parsed as
 * {@code null}) and no {@code mentionedAccountIds} (parsed as an empty list).
 * Neither is a parse failure — the contract mandates skip-and-dedupe, not DLQ, for
 * a missing recipient (community-events.md § Recipient-routing fields).
 *
 * <p>Forward compatibility: unknown payload fields are tolerated.
 */
@Component
public class CommunityEventParser {

    static final int SUPPORTED_SCHEMA_VERSION = 1;

    private final ObjectMapper objectMapper;

    public CommunityEventParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CommunityEvent parse(String rawValue) {
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
        String postId = requireText(payload, "postId");
        Instant occurredAt = requireInstant(payload, "occurredAt");
        // Optional by contract during the enrichment rollout — never a parse failure.
        String postAuthorAccountId = optionalText(payload, "postAuthorAccountId");

        String commentId = null;
        String actorAccountId;
        List<String> mentionedAccountIds = List.of();
        String reactionType = null;

        switch (eventType) {
            case NotificationType.EVENT_COMMENT_ADDED -> {
                commentId = requireText(payload, "commentId");
                actorAccountId = requireText(payload, "authorAccountId");
                mentionedAccountIds = optionalTextArray(payload, "mentionedAccountIds");
            }
            case NotificationType.EVENT_REACTION_ADDED -> {
                actorAccountId = requireText(payload, "reactorAccountId");
                reactionType = requireText(payload, "reactionType");
            }
            default -> throw new MalformedEventException("Unsupported eventType: " + eventType);
        }

        return new CommunityEvent(eventId, eventType, tenantId, postId, commentId,
                actorAccountId, postAuthorAccountId, mentionedAccountIds, reactionType,
                occurredAt);
    }

    private static String requireText(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull() || !node.isTextual() || node.asText().isBlank()) {
            throw new MalformedEventException("Missing required field: " + field);
        }
        return node.asText();
    }

    private static String optionalText(JsonNode parent, String field) {
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
    private static List<String> optionalTextArray(JsonNode parent, String field) {
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

    private static Instant requireInstant(JsonNode parent, String field) {
        String raw = requireText(parent, field);
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException e) {
            throw new MalformedEventException("Malformed timestamp in field " + field + ": " + raw);
        }
    }
}
