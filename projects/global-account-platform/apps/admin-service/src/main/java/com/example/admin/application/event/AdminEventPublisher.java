package com.example.admin.application.event;

import com.example.admin.application.Outcome;
import com.example.common.id.UuidV7;
import com.example.security.pii.PiiMaskingUtils;
import com.example.messaging.event.BaseEventPublisher;
import com.example.messaging.outbox.OutboxWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Publishes {@code admin.action.performed} events to the shared outbox in the
 * canonical envelope defined by
 * {@code specs/services/admin-service/data-model.md}:
 *
 * <pre>
 * {
 *   "eventId":   "UUID",
 *   "occurredAt":"ISO-8601 UTC ms",
 *   "actor":     {"type":"operator", "id": operatorId, "sessionId": jti},
 *   "action":    {"permission": key, "endpoint": uri, "method": httpMethod},
 *   "target":    {"type": ACCOUNT|SESSION|AUDIT_QUERY|..., "id": id, "displayHint": masked|null},
 *   "outcome":   SUCCESS|FAILURE|DENIED,
 *   "reason":    detail|null
 * }
 * </pre>
 *
 * <p>{@code target.displayHint} is produced centrally here via
 * {@link PiiMaskingUtils} when the target is an ACCOUNT identifier. All
 * other target types emit a {@code null} displayHint so the consumer never
 * receives raw PII (rules/traits/regulated.md R4).
 */
@Component
public class AdminEventPublisher extends BaseEventPublisher {

    private static final String AGGREGATE_TYPE = "AdminAction";
    private static final String EVENT_TYPE = "admin.action.performed";

    public AdminEventPublisher(OutboxWriter outboxWriter, ObjectMapper objectMapper) {
        super(outboxWriter, objectMapper);
    }

    public void publishAdminActionPerformed(Envelope env) {
        Map<String, Object> payload = new LinkedHashMap<>();
        // UUID v7 carries a 48-bit ms timestamp in its MSBs; improves outbox
        // index locality and natural time ordering (TASK-BE-028c).
        payload.put("eventId", UuidV7.randomString());
        payload.put("occurredAt", env.occurredAt() == null ? null : env.occurredAt().toString());

        Map<String, Object> actor = new LinkedHashMap<>();
        actor.put("type", "operator");
        actor.put("id", env.operatorId());
        actor.put("sessionId", env.sessionId());
        payload.put("actor", actor);

        Map<String, Object> action = new LinkedHashMap<>();
        action.put("permission", env.permission());
        action.put("endpoint", env.endpoint());
        action.put("method", env.method());
        payload.put("action", action);

        Map<String, Object> target = new LinkedHashMap<>();
        target.put("type", env.targetType());
        target.put("id", env.targetId());
        target.put("displayHint", displayHintFor(env.targetType(), env.targetId()));
        payload.put("target", target);

        payload.put("outcome", env.outcome() == null ? null : env.outcome().name());
        payload.put("reason", env.reason());

        String aggregateId = env.targetId() != null ? env.targetId() : "-";
        saveEvent(AGGREGATE_TYPE, aggregateId, EVENT_TYPE, payload);
    }

    private static String displayHintFor(String targetType, String targetId) {
        if (targetType == null || targetId == null) return null;
        if (!"ACCOUNT".equals(targetType)) return null;
        if (targetId.contains("@")) return PiiMaskingUtils.maskEmail(targetId);
        // UUID / numeric account ids are not PII; do not leak them into displayHint.
        return null;
    }

    /**
     * Canonical input record for {@link #publishAdminActionPerformed(Envelope)}.
     * Keeps the publisher decoupled from {@code AdminActionAuditor}'s records.
     */
    public record Envelope(
            String operatorId,
            String sessionId,
            String permission,
            String endpoint,
            String method,
            String targetType,
            String targetId,
            Outcome outcome,
            String reason,
            Instant occurredAt
    ) {}
}
