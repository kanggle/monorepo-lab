package com.example.fanplatform.notification.application.consumer;

import com.example.fanplatform.notification.domain.notification.NotificationType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
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
        EventEnvelope envelope = EventEnvelope.parse(objectMapper, rawValue, SUPPORTED_SCHEMA_VERSION);
        JsonNode payload = envelope.payload();
        String eventId = envelope.eventId();
        String eventType = envelope.eventType();

        String tenantId = JsonFields.requireText(payload, "tenantId");
        String postId = JsonFields.requireText(payload, "postId");
        Instant occurredAt = JsonFields.requireInstant(payload, "occurredAt");
        // Optional by contract during the enrichment rollout — never a parse failure.
        String postAuthorAccountId = JsonFields.optionalText(payload, "postAuthorAccountId");

        String commentId = null;
        String actorAccountId;
        List<String> mentionedAccountIds = List.of();
        String reactionType = null;

        switch (eventType) {
            case NotificationType.EVENT_COMMENT_ADDED -> {
                commentId = JsonFields.requireText(payload, "commentId");
                actorAccountId = JsonFields.requireText(payload, "authorAccountId");
                mentionedAccountIds = JsonFields.optionalTextArray(payload, "mentionedAccountIds");
            }
            case NotificationType.EVENT_REACTION_ADDED -> {
                actorAccountId = JsonFields.requireText(payload, "reactorAccountId");
                reactionType = JsonFields.requireText(payload, "reactionType");
            }
            default -> throw new MalformedEventException("Unsupported eventType: " + eventType);
        }

        return new CommunityEvent(eventId, eventType, tenantId, postId, commentId,
                actorAccountId, postAuthorAccountId, mentionedAccountIds, reactionType,
                occurredAt);
    }
}
