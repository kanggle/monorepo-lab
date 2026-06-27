package com.example.security.application.event;

import com.example.security.domain.detection.AccountLockClient;
import com.example.security.domain.pii.PiiMaskingRecord;
import com.example.security.domain.suspicious.SuspiciousEvent;

/**
 * Outbox-based publisher port for security-service Kafka events (TASK-BE-453 —
 * outbox v1 → v2). Previously a concrete {@code extends BaseEventPublisher}; now a
 * port whose v2 implementation is
 * {@link com.example.security.infrastructure.outbox.OutboxSecurityEventPublisher}
 * (the {@code AbstractOutboxPublisher} / {@code OutboxRow} path — ADR-MONO-004 § 5).
 *
 * <p>All events share the standard envelope declared in
 * {@code specs/contracts/events/security-events.md} (eventId, eventType, source,
 * occurredAt, schemaVersion, partitionKey, payload). The wire shape is preserved
 * byte-identically across the v1 → v2 swap (the v1 {@code BaseEventPublisher.writeEvent}
 * 7-field envelope, {@code source = "security-service"}).
 */
public interface SecurityEventPublisher {

    String TOPIC_SUSPICIOUS_DETECTED = "security.suspicious.detected";
    String TOPIC_AUTO_LOCK_TRIGGERED = "security.auto.lock.triggered";
    String TOPIC_AUTO_LOCK_PENDING = "security.auto.lock.pending";
    String TOPIC_PII_MASKED = "security.pii.masked";

    void publishSuspiciousDetected(SuspiciousEvent event);

    void publishAutoLockTriggered(SuspiciousEvent event, AccountLockClient.Status status);

    /**
     * Emitted when all retries to account-service have been exhausted. Consumed
     * by the operator manual-intervention path.
     */
    void publishAutoLockPending(SuspiciousEvent event);

    /**
     * TASK-BE-258: Emits {@code security.pii.masked} as a GDPR compliance audit trail.
     * Called by {@code PiiMaskingService} within the masking transaction (outbox pattern).
     *
     * @param record  the masking result carrying accountId, tenantId, maskedAt, tableNames
     * @param eventId the originating {@code account.deleted} event ID (used as idempotency
     *                reference; the outbox row mints its own UUIDv7 PK)
     */
    void publishPiiMasked(PiiMaskingRecord record, String eventId);
}
