package com.example.fanplatform.notification.application.consumer;

import com.example.fanplatform.notification.domain.notification.NotificationType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;

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
        EventEnvelope envelope = EventEnvelope.parse(objectMapper, rawValue, SUPPORTED_SCHEMA_VERSION);
        JsonNode payload = envelope.payload();
        String eventId = envelope.eventId();
        String eventType = envelope.eventType();

        String tenantId = JsonFields.requireText(payload, "tenantId");
        String accountId = JsonFields.requireText(payload, "accountId");
        String membershipId = JsonFields.requireText(payload, "membershipId");
        String tier = JsonFields.requireText(payload, "tier");

        Integer planMonths = null;
        Instant validFrom = null;
        Instant validTo = null;
        String reason = null;
        Instant canceledAt = null;

        switch (eventType) {
            case NotificationType.EVENT_ACTIVATED -> {
                planMonths = JsonFields.requireInt(payload, "planMonths");
                validFrom = JsonFields.requireInstant(payload, "validFrom");
                validTo = JsonFields.requireInstant(payload, "validTo");
            }
            case NotificationType.EVENT_CANCELED -> {
                reason = JsonFields.optionalText(payload, "reason");
                canceledAt = JsonFields.requireInstant(payload, "canceledAt");
            }
            case NotificationType.EVENT_EXPIRED -> validTo = JsonFields.requireInstant(payload, "validTo");
            default -> throw new MalformedEventException("Unsupported eventType: " + eventType);
        }

        return new MembershipEvent(eventId, eventType, tenantId, accountId, membershipId,
                tier, planMonths, validFrom, validTo, reason, canceledAt);
    }
}
