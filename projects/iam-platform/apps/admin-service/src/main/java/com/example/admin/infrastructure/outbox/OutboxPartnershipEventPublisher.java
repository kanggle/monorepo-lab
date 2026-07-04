package com.example.admin.infrastructure.outbox;

import com.example.admin.application.event.PartnershipEventPublisher;
import com.example.admin.domain.rbac.ScopeSet;
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
 * TASK-BE-477 / ADR-MONO-045 — {@link PartnershipEventPublisher} implementation.
 *
 * <p>Mirrors {@link OutboxTenantEventPublisher}: each method SELF-BUILDS the full
 * canonical 7-field envelope {@code {eventId, eventType, source="admin-service",
 * occurredAt, schemaVersion=1, partitionKey, payload}} and persists a pending
 * {@code admin_outbox} row (the SAME table as admin.action.performed / tenant.*)
 * driven by the {@link AdminOutboxPublisher} v2 relay. The self-minted {@code eventId}
 * (UUIDv7) is reused as the row PK so the relay's {@code eventId} Kafka header matches
 * the payload.
 *
 * <p><b>partitionKey = partnershipId</b> — orders a single partnership's lifecycle
 * (invite → accept → … → terminate). {@code partnership.terminated} is emitted ONCE
 * (one-shot cascade, D6) regardless of participant count.
 */
@Component
public class OutboxPartnershipEventPublisher implements PartnershipEventPublisher {

    private static final String AGGREGATE_TYPE = "Partnership";
    private static final String SOURCE = "admin-service";
    private static final int SCHEMA_VERSION = 1;

    private final AdminOutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxPartnershipEventPublisher(AdminOutboxJpaRepository outboxRepository,
                                           ObjectMapper objectMapper,
                                           Clock clock) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void publishInvited(String partnershipId, String hostTenantId, String partnerTenantId,
                               ScopeSet delegatedScope, String actorOperatorId, Instant invitedAt) {
        Map<String, Object> envelope = envelope("partnership.invited", partnershipId, invitedAt);
        Map<String, Object> inner = base(partnershipId, hostTenantId, partnerTenantId);
        inner.put("status", "PENDING");
        inner.put("delegatedScope", scopeMap(delegatedScope));
        inner.put("actor", actorOf(actorOperatorId));
        inner.put("invitedAt", invitedAt.toString());
        envelope.put("payload", inner);
        save(envelope, partnershipId, "partnership.invited");
    }

    @Override
    @Transactional
    public void publishAccepted(String partnershipId, String hostTenantId, String partnerTenantId,
                                String actorOperatorId, Instant acceptedAt) {
        Map<String, Object> envelope = envelope("partnership.accepted", partnershipId, acceptedAt);
        Map<String, Object> inner = base(partnershipId, hostTenantId, partnerTenantId);
        inner.put("previousStatus", "PENDING");
        inner.put("currentStatus", "ACTIVE");
        inner.put("actor", actorOf(actorOperatorId));
        inner.put("acceptedAt", acceptedAt.toString());
        envelope.put("payload", inner);
        save(envelope, partnershipId, "partnership.accepted");
    }

    @Override
    @Transactional
    public void publishSuspended(String partnershipId, String hostTenantId, String partnerTenantId,
                                 String reason, String actorOperatorId, Instant suspendedAt) {
        Map<String, Object> envelope = envelope("partnership.suspended", partnershipId, suspendedAt);
        Map<String, Object> inner = base(partnershipId, hostTenantId, partnerTenantId);
        inner.put("previousStatus", "ACTIVE");
        inner.put("currentStatus", "SUSPENDED");
        inner.put("reason", reason);
        inner.put("actor", actorOf(actorOperatorId));
        inner.put("suspendedAt", suspendedAt.toString());
        envelope.put("payload", inner);
        save(envelope, partnershipId, "partnership.suspended");
    }

    @Override
    @Transactional
    public void publishReactivated(String partnershipId, String hostTenantId, String partnerTenantId,
                                   String reason, String actorOperatorId, Instant reactivatedAt) {
        Map<String, Object> envelope = envelope("partnership.reactivated", partnershipId, reactivatedAt);
        Map<String, Object> inner = base(partnershipId, hostTenantId, partnerTenantId);
        inner.put("previousStatus", "SUSPENDED");
        inner.put("currentStatus", "ACTIVE");
        inner.put("reason", reason);
        inner.put("actor", actorOf(actorOperatorId));
        inner.put("reactivatedAt", reactivatedAt.toString());
        envelope.put("payload", inner);
        save(envelope, partnershipId, "partnership.reactivated");
    }

    @Override
    @Transactional
    public void publishTerminated(String partnershipId, String hostTenantId, String partnerTenantId,
                                  String previousStatus, String reason, int participantCountAtTermination,
                                  String actorOperatorId, Instant terminatedAt) {
        Map<String, Object> envelope = envelope("partnership.terminated", partnershipId, terminatedAt);
        Map<String, Object> inner = base(partnershipId, hostTenantId, partnerTenantId);
        inner.put("previousStatus", previousStatus);
        inner.put("currentStatus", "TERMINATED");
        inner.put("reason", reason);
        inner.put("participantCountAtTermination", participantCountAtTermination);
        inner.put("actor", actorOf(actorOperatorId));
        inner.put("terminatedAt", terminatedAt.toString());
        envelope.put("payload", inner);
        save(envelope, partnershipId, "partnership.terminated");
    }

    @Override
    @Transactional
    public void publishParticipantAdded(String partnershipId, String hostTenantId, String partnerTenantId,
                                        String operatorId, ScopeSet participantScope,
                                        String actorOperatorId, Instant assignedAt) {
        Map<String, Object> envelope = envelope("partnership.participant_added", partnershipId, assignedAt);
        Map<String, Object> inner = base(partnershipId, hostTenantId, partnerTenantId);
        inner.put("operatorId", operatorId);
        if (participantScope != null) {
            inner.put("participantScope", scopeMap(participantScope));
        }
        inner.put("actor", actorOf(actorOperatorId));
        inner.put("assignedAt", assignedAt.toString());
        envelope.put("payload", inner);
        save(envelope, partnershipId, "partnership.participant_added");
    }

    @Override
    @Transactional
    public void publishParticipantRemoved(String partnershipId, String hostTenantId, String partnerTenantId,
                                          String operatorId, String actorOperatorId, Instant removedAt) {
        Map<String, Object> envelope = envelope("partnership.participant_removed", partnershipId, removedAt);
        Map<String, Object> inner = base(partnershipId, hostTenantId, partnerTenantId);
        inner.put("operatorId", operatorId);
        inner.put("actor", actorOf(actorOperatorId));
        inner.put("removedAt", removedAt.toString());
        envelope.put("payload", inner);
        save(envelope, partnershipId, "partnership.participant_removed");
    }

    // ── envelope helpers (mirror OutboxTenantEventPublisher) ────────────────────

    private static Map<String, Object> envelope(String eventType, String partnershipId, Instant occurredAt) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", UuidV7.randomString());
        envelope.put("eventType", eventType);
        envelope.put("source", SOURCE);
        envelope.put("occurredAt", occurredAt.toString());
        envelope.put("schemaVersion", SCHEMA_VERSION);
        envelope.put("partitionKey", partnershipId);
        return envelope;
    }

    private static Map<String, Object> base(String partnershipId, String hostTenantId, String partnerTenantId) {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("partnershipId", partnershipId);
        inner.put("hostTenantId", hostTenantId);
        inner.put("partnerTenantId", partnerTenantId);
        return inner;
    }

    private static Map<String, Object> scopeMap(ScopeSet scope) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("domains", scope == null ? java.util.List.of() : scope.domains());
        m.put("roles", scope == null ? java.util.List.of() : scope.roles());
        return m;
    }

    private static Map<String, Object> actorOf(String operatorId) {
        Map<String, Object> actor = new LinkedHashMap<>();
        actor.put("type", "operator");
        actor.put("id", operatorId);
        return actor;
    }

    private void save(Map<String, Object> envelope, String partnershipId, String eventType) {
        String json;
        try {
            json = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "failed to serialise " + eventType + " outbox envelope", e);
        }
        UUID eventId = UUID.fromString((String) envelope.get("eventId"));
        outboxRepository.save(AdminOutboxJpaEntity.create(
                eventId, AGGREGATE_TYPE, partnershipId, eventType, json, partnershipId,
                Instant.now(clock)));
    }
}
