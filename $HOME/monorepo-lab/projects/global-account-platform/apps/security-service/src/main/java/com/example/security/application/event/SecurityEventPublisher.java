package com.example.security.application.event;

import com.example.messaging.event.BaseEventPublisher;
import com.example.messaging.outbox.OutboxWriter;
import com.example.security.domain.detection.AccountLockClient;
import com.example.security.domain.pii.PiiMaskingRecord;
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
    public static final String TOPIC_PII_MASKED = "security.pii.masked";

    private static final String AGGREGATE_TYPE = "security";
    private static final String SOURCE = "security-service";

    public SecurityEventPublisher(OutboxWriter outboxWriter, ObjectMapper objectMapper) {
        super(outboxWriter, objectMapper);
    }

    /**
     * TASK-BE-248 Phase 2b: every publish method must carry a non-blank tenantId.
     * {@link SuspiciousEvent} already rejects null tenantId at construction time, so
     * this guard is a final defence against a blank-string edge case.
     */
    private static void requireTenantId(SuspiciousEvent event) {
        String tenantId = event.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException(
                    "tenantId required for SecurityEvent publish (eventId=" + event.getId() + ")");
        }
    }

    public void publishSuspiciousDetected(SuspiciousEvent event) {
        requireTenantId(event);
        Map<String, Object> payload = buildSuspiciousEventBase(event);
        payload.put("actionTaken", event.getActionTaken().name());
        payload.put("evidence", event.getEvidence());
        payload.put("triggerEventId", event.getTriggerEventId());
        payload.put("detectedAt", event.getDetectedAt().toString());
        writeEnvelope(TOPIC_SUSPICIOUS_DETECTED, event.getAccountId(), payload);
    }

    public void publishAutoLockTriggered(SuspiciousEvent event, AccountLockClient.Status status) {
        requireTenantId(event);
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
        requireTenantId(event);
        Map<String, Object> payload = buildSuspiciousEventBase(event);
        payload.put("reason", "ACCOUNT_SERVICE_UNREACHABLE");
        payload.put("raisedAt", Instant.now().toString());
        writeEnvelope(TOPIC_AUTO_LOCK_PENDING, event.getAccountId(), payload);
    }

    /**
     * Builds the common fields shared by every {@link SuspiciousEvent}-based publish
     * method in the documented insertion order: {@code suspiciousEventId, tenantId,
     * accountId, ruleCode, riskScore}. Returned as a mutable {@link LinkedHashMap} so
     * callers can append method-specific fields after the common prefix. Mirrors the
     * {@code AuthEventPublisher#buildLoginSucceededBase} pattern (TASK-BE-131).
     *
     * <p>TASK-BE-248 Phase 1: {@code tenant_id} is now always included in the outbox
     * payload so downstream consumers can validate the field presence. Null/blank tenantId
     * is rejected by {@link #requireTenantId(SuspiciousEvent)} before this is called.
     */
    private Map<String, Object> buildSuspiciousEventBase(SuspiciousEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("suspiciousEventId", event.getId());
        payload.put("tenantId", event.getTenantId());
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

    /**
     * TASK-BE-258: Emits {@code security.pii.masked} as a GDPR compliance audit trail.
     * Called by {@code PiiMaskingService} within the masking transaction (outbox pattern).
     *
     * @param record  the masking result carrying accountId, tenantId, maskedAt, tableNames
     * @param eventId the originating {@code account.deleted} event ID (used as idempotency
     *                reference in the outbox; the outbox writer will generate a new UUID for
     *                the outbox row itself)
     */
    public void publishPiiMasked(PiiMaskingRecord record, String eventId) {
        if (record.tenantId() == null || record.tenantId().isBlank()) {
            throw new IllegalArgumentException(
                    "tenantId required for PiiMasked publish (sourceEventId=" + eventId + ")");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("accountId", record.accountId());
        payload.put("tenantId", record.tenantId());
        payload.put("maskedAt", record.maskedAt().toString());
        payload.put("tableNames", record.tableNames());
        writeEnvelope(TOPIC_PII_MASKED, record.accountId(), payload);
    }

    private void writeEnvelope(String eventType, String partitionKey, Map<String, Object> payload) {
        writeEvent(AGGREGATE_TYPE, partitionKey, eventType, SOURCE, payload);
    }
}
