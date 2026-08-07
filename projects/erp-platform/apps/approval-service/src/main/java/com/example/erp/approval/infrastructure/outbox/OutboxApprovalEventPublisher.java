package com.example.erp.approval.infrastructure.outbox;

import com.example.common.id.UuidV7;
import com.example.erp.approval.application.event.ApprovalEventPublisher;
import com.example.erp.approval.domain.delegation.DelegationGrant;
import com.example.erp.approval.domain.request.ApprovalRequest;
import com.example.erp.approval.infrastructure.persistence.jpa.ApprovalOutboxJpaEntity;
import com.example.erp.approval.infrastructure.persistence.jpa.ApprovalOutboxJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * {@link ApprovalEventPublisher} implementation (TASK-ERP-BE-025 — outbox v1 → v2).
 *
 * <p>Builds the canonical event envelope and persists an {@code approval_outbox}
 * row in the caller's transaction (the {@code AbstractOutboxPublisher} /
 * {@code OutboxRow} path — ADR-MONO-004 § 5, mirroring finance account-service's
 * {@code OutboxAccountEventPublisher}). The {@code ApprovalOutboxPublisher} relay
 * forwards the row to Kafka asynchronously; downstream consumers dedupe on the
 * envelope {@code eventId} (at-least-once).
 *
 * <p><b>Envelope conforms to the contract</b>
 * ({@code specs/contracts/events/erp-approval-events.md} § Envelope,
 * TASK-ERP-BE-043). This publisher inherited a 7-field shape
 * ({@code {eventId, eventType, source, occurredAt, schemaVersion, partitionKey,
 * payload}}) that the contract never declared: the contract has always specified
 * top-level {@code tenantId}, {@code aggregateType} ("{@code aggregateType} is
 * {@code ApprovalRequest} on <b>every</b> approval event") and {@code aggregateId},
 * and both read-model consumers ({@code ApprovalEventEnvelope.isValid()},
 * {@code DelegationEventEnvelope.isValid()}) require the top-level
 * {@code aggregateId}. Every {@code erp.approval.*} message therefore routed
 * straight to {@code .DLT} — unobservable until TASK-ERP-BE-042 started the relay
 * and TASK-ERP-BE-041 unblocked submit.
 *
 * <p>This is the <b>same defect the sibling already fixed</b>: TASK-ERP-BE-032
 * corrected the identical 7-field omission in
 * {@code OutboxMasterdataEventPublisher} ("rejected every real event to
 * {@code .DLT}") and the approval twin was never brought along. The sourcing rule
 * here is copied from it verbatim — {@code tenantId} off the payload,
 * {@code aggregateType}/{@code aggregateId} off the call — so the two producers now
 * emit one shape from one rule. {@code traceId} stays deferred in v1 (the relay
 * does not propagate W3C trace context; consumers treat it as optional).
 *
 * <p>{@code partitionKey} (= {@code aggregateId}) and the envelope {@code eventId}
 * (= the {@code approval_outbox} PK, both UUIDv7) are unchanged, as is every
 * payload field/order and every NON_NULL omission.
 */
@Component
public class OutboxApprovalEventPublisher implements ApprovalEventPublisher {

    static final String SOURCE = "erp-platform-approval-service";
    private static final int SCHEMA_VERSION = 1;

    private final ApprovalOutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxApprovalEventPublisher(ApprovalOutboxJpaRepository outboxRepository,
                                        ObjectMapper objectMapper,
                                        Clock clock) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void publishSubmitted(ApprovalRequest r, String actor) {
        writeEvent(AGGREGATE_TYPE, r.getId(), EVENT_APPROVAL_SUBMITTED,
                payload(r, actor, null, null, null));
    }

    @Override
    public void publishApproved(ApprovalRequest r, String actor, String reason,
                                String actingForApproverId) {
        writeEvent(AGGREGATE_TYPE, r.getId(), EVENT_APPROVAL_APPROVED,
                payload(r, actor, r.getFinalizedAt(), reason, actingForApproverId));
    }

    @Override
    public void publishRejected(ApprovalRequest r, String actor, String reason,
                                String actingForApproverId) {
        writeEvent(AGGREGATE_TYPE, r.getId(), EVENT_APPROVAL_REJECTED,
                payload(r, actor, r.getFinalizedAt(), reason, actingForApproverId));
    }

    @Override
    public void publishWithdrawn(ApprovalRequest r, String actor, String reason) {
        writeEvent(AGGREGATE_TYPE, r.getId(), EVENT_APPROVAL_WITHDRAWN,
                payload(r, actor, r.getFinalizedAt(), reason, null));
    }

    @Override
    public void publishDelegated(DelegationGrant g, String actor) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("grantId", g.getId());
        p.put("delegatorId", g.getDelegatorId());
        p.put("delegateId", g.getDelegateId());
        p.put("validFrom", g.getValidFrom().toString());
        if (g.getValidTo() != null) {
            p.put("validTo", g.getValidTo().toString());
        }
        if (g.getReason() != null && !g.getReason().isBlank()) {
            p.put("reason", g.getReason());
        }
        // TASK-ERP-BE-017 — scope always present; scopeRequestId only for REQUEST
        // (NON_NULL absent for GLOBAL). Producer-only forward (BE-018 / PC-FE-056
        // project later; current consumers ignore the unknown fields).
        p.put("scope", g.getScope().name());
        if (g.getScopeRequestId() != null && !g.getScopeRequestId().isBlank()) {
            p.put("scopeRequestId", g.getScopeRequestId());
        }
        p.put("tenantId", g.getTenantId());
        p.put("occurredAt", Instant.now().toString());
        p.put("actor", actor);
        writeEvent(DELEGATION_AGGREGATE_TYPE, g.getId(), EVENT_APPROVAL_DELEGATED, p);
    }

    @Override
    public void publishRevoked(DelegationGrant g, String actor) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("grantId", g.getId());
        p.put("delegatorId", g.getDelegatorId());
        p.put("delegateId", g.getDelegateId());
        if (g.getReason() != null && !g.getReason().isBlank()) {
            p.put("reason", g.getReason());
        }
        p.put("tenantId", g.getTenantId());
        p.put("occurredAt", Instant.now().toString());
        p.put("actor", actor);
        writeEvent(DELEGATION_AGGREGATE_TYPE, g.getId(),
                EVENT_APPROVAL_DELEGATION_REVOKED, p);
    }

    /**
     * Common approval payload (erp-approval-events.md § Payload schemas + v2.0
     * amendment). {@code finalizedAt} ABSENT on submitted; {@code reason} ABSENT
     * when none (a {@code null} map value is dropped — here we simply omit the
     * key). Additive v2.0 fields {@code currentStage} (0-based) + {@code
     * totalStages} are always present (existing consumers ignore unknown
     * properties). {@code approverId} = the relevant stage's approver (submitted
     * → stage 0; approved → the final stage — both = the denormalized current
     * {@code approverId} at emit time). Copied VERBATIM from the v1
     * {@code ApprovalEventPublisher.payload}.
     */
    private static Map<String, Object> payload(ApprovalRequest r, String actor,
                                               Instant finalizedAt, String reason,
                                               String actingForApproverId) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("approvalRequestId", r.getId());
        p.put("subjectType", r.getSubjectType().name());
        p.put("subjectId", r.getSubjectId());
        p.put("approverId", r.getApproverId());
        p.put("submitterId", r.getSubmitterId());
        p.put("tenantId", r.getTenantId());
        p.put("occurredAt", Instant.now().toString());
        p.put("actor", actor);
        p.put("currentStage", r.getCurrentStageIndex());
        p.put("totalStages", r.getTotalStages());
        if (finalizedAt != null) {
            p.put("finalizedAt", finalizedAt.toString());
        }
        if (reason != null && !reason.isBlank()) {
            p.put("reason", reason);
        }
        // TASK-ERP-BE-013 — present only when a delegate acted (NON_NULL absent
        // otherwise); existing consumers ignore the unknown field.
        if (actingForApproverId != null && !actingForApproverId.isBlank()) {
            p.put("actingForApproverId", actingForApproverId);
        }
        return p;
    }

    /**
     * Wrap {@code payload} in the contract envelope, serialise it, and persist a
     * pending {@code approval_outbox} row in the caller's transaction. The generated
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
        // TASK-ERP-BE-043 — the three contract fields this publisher had been
        // omitting. Sourced exactly as OutboxMasterdataEventPublisher sources them
        // (TASK-ERP-BE-032), so the two producers now emit ONE envelope shape from
        // ONE rule rather than two shapes that happened to share five keys.
        envelope.put("tenantId", payload.get("tenantId"));
        envelope.put("aggregateType", aggregateType);
        envelope.put("aggregateId", aggregateId);
        envelope.put("partitionKey", aggregateId);
        envelope.put("payload", payload);

        String json;
        try {
            json = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "failed to serialise " + eventType + " outbox envelope", e);
        }

        outboxRepository.save(ApprovalOutboxJpaEntity.create(
                eventId, aggregateType, aggregateId, eventType,
                json, aggregateId, occurredAt));
    }
}
