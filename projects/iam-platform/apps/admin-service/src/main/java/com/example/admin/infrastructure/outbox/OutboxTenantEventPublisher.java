package com.example.admin.infrastructure.outbox;

import com.example.admin.application.event.TenantEventPublisher;
import com.example.admin.infrastructure.persistence.AdminOutboxJpaEntity;
import com.example.admin.infrastructure.persistence.AdminOutboxJpaRepository;
import com.example.common.id.UuidV7;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link TenantEventPublisher} implementation (TASK-BE-452 — outbox v1 → v2).
 *
 * <p><b>Judgment call — already self-builds a FULL 7-field envelope.</b> Unlike
 * {@code AdminEventPublisher} (flat), the v1 {@code TenantEventPublisher} built the
 * complete canonical envelope {@code {eventId, eventType, source="admin-service",
 * occurredAt, schemaVersion=1, partitionKey, payload}} INSIDE each method and passed
 * THAT map to {@code BaseEventPublisher.saveEvent} (serialize-as-is). So the on-wire
 * Kafka value is the full envelope. This adapter reproduces those EXACT bytes — it
 * does NOT call a generic envelope-wrapper (that would double-wrap). Each method's
 * {@link LinkedHashMap} construction (field order, inner {@code payload} composition,
 * {@code actor} sub-map) is copied VERBATIM from the v1 publisher.
 *
 * <p>The v1 envelope already mints its own {@code eventId} (UUIDv7); that value is
 * reused as the {@code admin_outbox} row PK so the relay's additive {@code eventId}
 * Kafka header matches the payload. Persisted into the SAME {@code admin_outbox} table
 * as {@code admin.action.performed}.
 */
@Component
public class OutboxTenantEventPublisher implements TenantEventPublisher {

    private static final String AGGREGATE_TYPE = "Tenant";
    private static final String SOURCE = "admin-service";
    private static final int SCHEMA_VERSION = 1;

    private final AdminOutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxTenantEventPublisher(AdminOutboxJpaRepository outboxRepository,
                                      ObjectMapper objectMapper,
                                      Clock clock) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void publishTenantCreated(String tenantId, String displayName, String tenantType,
                                     String operatorId, Instant createdAt) {
        Map<String, Object> envelope = envelope("tenant.created", tenantId, createdAt);

        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("tenantId", tenantId);
        inner.put("displayName", displayName);
        inner.put("tenantType", tenantType);
        inner.put("status", "ACTIVE");
        inner.put("actor", actorOf(operatorId));
        inner.put("createdAt", createdAt.toString());
        envelope.put("payload", inner);

        save(envelope, tenantId, "tenant.created");
    }

    @Override
    @Transactional
    public void publishTenantSuspended(String tenantId, String operatorId,
                                       String reason, Instant suspendedAt) {
        Map<String, Object> envelope = envelope("tenant.suspended", tenantId, suspendedAt);

        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("tenantId", tenantId);
        inner.put("previousStatus", "ACTIVE");
        inner.put("currentStatus", "SUSPENDED");
        inner.put("reason", reason);
        inner.put("actor", actorOf(operatorId));
        inner.put("suspendedAt", suspendedAt.toString());
        envelope.put("payload", inner);

        save(envelope, tenantId, "tenant.suspended");
    }

    @Override
    @Transactional
    public void publishTenantReactivated(String tenantId, String operatorId,
                                         String reason, Instant reactivatedAt) {
        Map<String, Object> envelope = envelope("tenant.reactivated", tenantId, reactivatedAt);

        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("tenantId", tenantId);
        inner.put("previousStatus", "SUSPENDED");
        inner.put("currentStatus", "ACTIVE");
        inner.put("reason", reason);
        inner.put("actor", actorOf(operatorId));
        inner.put("reactivatedAt", reactivatedAt.toString());
        envelope.put("payload", inner);

        save(envelope, tenantId, "tenant.reactivated");
    }

    @Override
    @Transactional
    public void publishTenantUpdated(String tenantId, String displayNameFrom, String displayNameTo,
                                     String operatorId, Instant updatedAt) {
        Map<String, Object> envelope = envelope("tenant.updated", tenantId, updatedAt);

        Map<String, Object> changes = new LinkedHashMap<>();
        Map<String, Object> displayNameChange = new LinkedHashMap<>();
        displayNameChange.put("from", displayNameFrom);
        displayNameChange.put("to", displayNameTo);
        changes.put("displayName", displayNameChange);

        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("tenantId", tenantId);
        inner.put("changes", changes);
        inner.put("actor", actorOf(operatorId));
        inner.put("updatedAt", updatedAt.toString());
        envelope.put("payload", inner);

        save(envelope, tenantId, "tenant.updated");
    }

    /**
     * Builds the first six fields of the v1 self-built envelope VERBATIM
     * ({@code eventId, eventType, source, occurredAt, schemaVersion, partitionKey});
     * the caller appends the inner {@code payload}. {@code occurredAt} uses the
     * event timestamp argument (matching v1 — NOT a clock read).
     */
    private static Map<String, Object> envelope(String eventType, String tenantId, Instant occurredAt) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", UuidV7.randomString());
        envelope.put("eventType", eventType);
        envelope.put("source", SOURCE);
        envelope.put("occurredAt", occurredAt.toString());
        envelope.put("schemaVersion", SCHEMA_VERSION);
        envelope.put("partitionKey", tenantId);
        return envelope;
    }

    private static Map<String, Object> actorOf(String operatorId) {
        Map<String, Object> actor = new LinkedHashMap<>();
        actor.put("type", "operator");
        actor.put("id", operatorId);
        return actor;
    }

    /**
     * Serialise the SELF-BUILT envelope AS-IS (byte-identical to the v1
     * {@code saveEvent} wire — no double-wrap) and persist a pending
     * {@code admin_outbox} row. The row PK reuses the envelope's own {@code eventId};
     * {@code partition_key = tenantId} (the v1 Kafka key).
     */
    private void save(Map<String, Object> envelope, String tenantId, String eventType) {
        String json;
        try {
            json = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "failed to serialise " + eventType + " outbox envelope", e);
        }
        UUID eventId = UUID.fromString((String) envelope.get("eventId"));
        outboxRepository.save(AdminOutboxJpaEntity.create(
                eventId, AGGREGATE_TYPE, tenantId, eventType, json, tenantId,
                Instant.now(clock)));
    }
}
