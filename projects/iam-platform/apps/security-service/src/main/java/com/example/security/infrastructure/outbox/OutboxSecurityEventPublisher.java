package com.example.security.infrastructure.outbox;

import com.example.common.id.UuidV7;
import com.example.security.application.event.SecurityEventPublisher;
import com.example.security.domain.detection.AccountLockClient;
import com.example.security.domain.pii.PiiMaskingRecord;
import com.example.security.domain.suspicious.SuspiciousEvent;
import com.example.security.infrastructure.persistence.SecurityOutboxJpaEntity;
import com.example.security.infrastructure.persistence.SecurityOutboxJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link SecurityEventPublisher} implementation (TASK-BE-453 — outbox v1 → v2).
 *
 * <p>Builds the canonical event envelope and persists a {@code security_outbox}
 * row in the caller's transaction (the {@code AbstractOutboxPublisher} /
 * {@code OutboxRow} path — ADR-MONO-004 § 5, mirroring the in-worktree auth-service's
 * {@code OutboxAuthEventPublisher} + finance account-service's
 * {@code OutboxAccountEventPublisher}). The {@code SecurityOutboxPublisher} relay
 * forwards the row to Kafka asynchronously; downstream consumers dedupe on the
 * envelope {@code eventId} (at-least-once).
 *
 * <p><b>Wire-shape preserved.</b> The envelope is the EXACT 7-field shape the
 * previous {@code BaseEventPublisher.writeEvent} path emitted —
 * {@code {eventId, eventType, source, occurredAt, schemaVersion, partitionKey,
 * payload}}, {@code source = "security-service"}, every payload field/order
 * unchanged — so consumers are unaffected. Each publish method's payload-Map
 * construction is copied VERBATIM from the v1 {@code SecurityEventPublisher}
 * (TASK-BE-248 tenant_id presence + field-order invariants preserved). The only
 * change: the envelope {@code eventId} now equals the {@code security_outbox} PK
 * (both UUIDv7) so the Kafka {@code eventId} header matches the payload.
 *
 * <p><b>Transaction semantics preserved.</b> The three {@link SuspiciousEvent}-based
 * publish methods keep the v1 {@code @Transactional(REQUIRED)} (TASK-MONO-046-8a:
 * each opens its own outbox-write TX, or participates in the caller's). The
 * {@link #publishPiiMasked} method keeps NO {@code @Transactional} — it is called by
 * {@code PiiMaskingService} within the masking transaction (outbox pattern).
 */
@Component
public class OutboxSecurityEventPublisher implements SecurityEventPublisher {

    private static final String AGGREGATE_TYPE = "security";
    private static final String SOURCE = "security-service";
    private static final int SCHEMA_VERSION = 1;

    private final SecurityOutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxSecurityEventPublisher(SecurityOutboxJpaRepository outboxRepository,
                                        ObjectMapper objectMapper,
                                        Clock clock) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * TASK-BE-248 Phase 2b: every publish method must carry a non-blank tenantId.
     * {@link SuspiciousEvent} already rejects null tenantId at construction time, so
     * this guard is a final defence against a blank-string edge case. Verbatim from
     * the v1 publisher.
     */
    private static void requireTenantId(SuspiciousEvent event) {
        String tenantId = event.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException(
                    "tenantId required for SecurityEvent publish (eventId=" + event.getId() + ")");
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void publishSuspiciousDetected(SuspiciousEvent event) {
        requireTenantId(event);
        Map<String, Object> payload = buildSuspiciousEventBase(event);
        payload.put("actionTaken", event.getActionTaken().name());
        payload.put("evidence", event.getEvidence());
        payload.put("triggerEventId", event.getTriggerEventId());
        payload.put("detectedAt", event.getDetectedAt().toString());
        writeEnvelope(TOPIC_SUSPICIOUS_DETECTED, event.getAccountId(), payload);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void publishAutoLockTriggered(SuspiciousEvent event, AccountLockClient.Status status) {
        requireTenantId(event);
        Map<String, Object> payload = buildSuspiciousEventBase(event);
        payload.put("lockRequestResult", mapStatus(status));
        payload.put("lockRequestedAt", Instant.now().toString());
        writeEnvelope(TOPIC_AUTO_LOCK_TRIGGERED, event.getAccountId(), payload);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
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
     * callers can append method-specific fields after the common prefix. Verbatim
     * from the v1 publisher (TASK-BE-131 / TASK-BE-248 Phase 1).
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
     * (SUCCESS | ALREADY_LOCKED | FAILURE). Verbatim from the v1 publisher.
     */
    private String mapStatus(AccountLockClient.Status status) {
        return switch (status) {
            case SUCCESS -> "SUCCESS";
            case ALREADY_LOCKED -> "ALREADY_LOCKED";
            case INVALID_TRANSITION, FAILURE -> "FAILURE";
        };
    }

    @Override
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
        writeEvent(AGGREGATE_TYPE, partitionKey, eventType, payload);
    }

    /**
     * Wrap {@code payload} in the canonical 7-field envelope (preserved from the
     * v1 {@code BaseEventPublisher.writeEvent} path), serialise it, and persist a
     * pending {@code security_outbox} row in the caller's transaction. The generated
     * {@link UuidV7} doubles as the envelope {@code eventId} and the row PK;
     * {@code partition_key = aggregateId} (the v1 Kafka key).
     */
    private void writeEvent(String aggregateType, String aggregateId,
                            String eventType, Map<String, Object> payload) {
        UUID eventId = UuidV7.randomUuid();
        Instant occurredAt = Instant.now(clock);

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", eventType);
        envelope.put("source", SOURCE);
        envelope.put("occurredAt", occurredAt.toString());
        envelope.put("schemaVersion", SCHEMA_VERSION);
        envelope.put("partitionKey", aggregateId);
        envelope.put("payload", payload);

        String json;
        try {
            json = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "failed to serialise " + eventType + " outbox envelope", e);
        }

        outboxRepository.save(SecurityOutboxJpaEntity.create(
                eventId, aggregateType, aggregateId, eventType,
                json, aggregateId, occurredAt));
    }
}
