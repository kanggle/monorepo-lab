package com.example.security.application.event;

import com.example.messaging.event.BaseEventPublisher;
import com.example.messaging.outbox.OutboxWriter;
import com.example.security.domain.detection.AccountLockClient;
import com.example.security.domain.suspicious.SuspiciousEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Outbox-based publisher for security-service Kafka events.
 *
 * <p>All events share the standard envelope declared in
 * {@code specs/contracts/events/auth-events.md} (eventId, eventType, source,
 * occurredAt, schemaVersion, partitionKey, payload).</p>
 */
@Component
public class SecurityEventPublisher extends BaseEventPublisher {

    public static final String TOPIC_SUSPICIOUS_DETECTED = "security.suspicious.detected";
    public static final String TOPIC_AUTO_LOCK_TRIGGERED = "security.auto.lock.triggered";
    public static final String TOPIC_AUTO_LOCK_PENDING = "security.auto.lock.pending";

    private static final String AGGREGATE_TYPE = "security";
    private static final String SOURCE = "security-service";

    public SecurityEventPublisher(OutboxWriter outboxWriter, ObjectMapper objectMapper) {
        super(outboxWriter, objectMapper);
    }

    public void publishSuspiciousDetected(SuspiciousEvent event) {
        Map<String, Object> payload = buildSuspiciousEventBase(event);
        payload.put("actionTaken", event.getActionTaken().name());
        payload.put("evidence", event.getEvidence());
        payload.put("triggerEventId", event.getTriggerEventId());
        payload.put("detectedAt", event.getDetectedAt().toString());
        writeEnvelope(TOPIC_SUSPICIOUS_DETECTED, event.getAccountId(), payload);
    }

    public void publishAutoLockTriggered(SuspiciousEvent event, AccountLockClient.Status status) {
        Map<String, Object> payload = buildSuspiciousEventBase(event);
        payload.put("lockRequestResult", mapStatus(status));
        payload.put("lockRequestedAt", Instant.now().toString());
        writeEnvelope(TOPIC_AUTO_LOCK_TRIGGERED, event.getAccountId(), payload);
    }

    /**
     * Emitted when all retries to account-service have been exhausted. Consumed
     * by the operator manual-intervention path.
     */
    public void publishAutoLockPending(SuspiciousEvent event) {
        Map<String, Object> payload = buildSuspiciousEventBase(event);
        payload.put("reason", "ACCOUNT_SERVICE_UNREACHABLE");
        payload.put("raisedAt", Instant.now().toString());
        writeEnvelope(TOPIC_AUTO_LOCK_PENDING, event.getAccountId(), payload);
    }

    /**
     * Builds the 4 common fields shared by every {@link SuspiciousEvent}-based publish
     * method in the documented insertion order: {@code suspiciousEventId, accountId,
     * ruleCode, riskScore}. Returned as a mutable {@link LinkedHashMap} so callers can
     * append method-specific fields after the common prefix. Mirrors the
     * {@code AuthEventPublisher#buildLoginSucceededBase} pattern (TASK-BE-131).
     */
    private Map<String, Object> buildSuspiciousEventBase(SuspiciousEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("suspiciousEventId", event.getId());
        payload.put("accountId", event.getAccountId());
        payload.put("ruleCode", event.getRuleCode());
        payload.put("riskScore", event.getRiskScore());
        return payload;
    }

    /**
     * Maps the client-side status enum to the normalized contract vocabulary
     * (SUCCESS | ALREADY_LOCKED | FAILURE). This MUST match the value persisted
     * to {@code suspicious_events.lock_request_result} — see
     * {@code DetectSuspiciousActivityUseCase#triggerAutoLock}.
     */
    private String mapStatus(AccountLockClient.Status status) {
        return switch (status) {
            case SUCCESS -> "SUCCESS";
            case ALREADY_LOCKED -> "ALREADY_LOCKED";
            case INVALID_TRANSITION, FAILURE -> "FAILURE";
        };
    }

    private void writeEnvelope(String eventType, String partitionKey, Map<String, Object> payload) {
        writeEvent(AGGREGATE_TYPE, partitionKey, eventType, SOURCE, payload);
    }
}
