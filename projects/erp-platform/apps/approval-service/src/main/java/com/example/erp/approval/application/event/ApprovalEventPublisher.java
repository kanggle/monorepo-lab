package com.example.erp.approval.application.event;

import com.example.erp.approval.domain.delegation.DelegationGrant;
import com.example.erp.approval.domain.request.ApprovalRequest;
import com.example.erp.approval.domain.request.ApprovalStatus;
import com.example.messaging.event.BaseEventPublisher;
import com.example.messaging.outbox.OutboxWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Appends {@code erp.approval.{submitted,approved,rejected,withdrawn}} events to
 * the transactional outbox (architecture.md § Outbox + audit_log invariants).
 * Every {@code publish*} call happens INSIDE the use-case {@code @Transactional}
 * boundary so the event write commits atomically with the state change + audit
 * row (erp E4 / A7 atomicity). partition key = {@code approvalRequestId}.
 *
 * <p>Contract: {@code specs/contracts/events/erp-approval-events.md}.
 */
@Component
public class ApprovalEventPublisher extends BaseEventPublisher {

    public static final String SOURCE = "erp-platform-approval-service";
    public static final String AGGREGATE_TYPE = "ApprovalRequest";
    /** TASK-ERP-BE-013 — the delegated event's aggregate type. */
    public static final String DELEGATION_AGGREGATE_TYPE = "DelegationGrant";

    public static final String EVENT_APPROVAL_SUBMITTED = "erp.approval.submitted";
    public static final String EVENT_APPROVAL_APPROVED = "erp.approval.approved";
    public static final String EVENT_APPROVAL_REJECTED = "erp.approval.rejected";
    public static final String EVENT_APPROVAL_WITHDRAWN = "erp.approval.withdrawn";
    /** TASK-ERP-BE-013 — new topic, emitted on DelegationGrant create. */
    public static final String EVENT_APPROVAL_DELEGATED = "erp.approval.delegated";
    /** TASK-ERP-BE-015 — new topic, emitted on DelegationGrant revoke (ACTIVE→REVOKED). */
    public static final String EVENT_APPROVAL_DELEGATION_REVOKED = "erp.approval.delegation.revoked";

    public ApprovalEventPublisher(OutboxWriter outboxWriter, ObjectMapper objectMapper) {
        super(outboxWriter, objectMapper);
    }

    public void publishSubmitted(ApprovalRequest r, String actor) {
        writeEvent(AGGREGATE_TYPE, r.getId(), EVENT_APPROVAL_SUBMITTED, SOURCE,
                payload(r, actor, null, null, null));
    }

    /**
     * {@code actingForApproverId} (TASK-ERP-BE-013) = the stage approver A when a
     * delegate performed the approval on A's behalf; {@code null} (ABSENT) when the
     * approver acted themselves.
     */
    public void publishApproved(ApprovalRequest r, String actor, String reason,
                                String actingForApproverId) {
        writeEvent(AGGREGATE_TYPE, r.getId(), EVENT_APPROVAL_APPROVED, SOURCE,
                payload(r, actor, r.getFinalizedAt(), reason, actingForApproverId));
    }

    public void publishRejected(ApprovalRequest r, String actor, String reason,
                                String actingForApproverId) {
        writeEvent(AGGREGATE_TYPE, r.getId(), EVENT_APPROVAL_REJECTED, SOURCE,
                payload(r, actor, r.getFinalizedAt(), reason, actingForApproverId));
    }

    public void publishWithdrawn(ApprovalRequest r, String actor, String reason) {
        writeEvent(AGGREGATE_TYPE, r.getId(), EVENT_APPROVAL_WITHDRAWN, SOURCE,
                payload(r, actor, r.getFinalizedAt(), reason, null));
    }

    /**
     * NEW topic {@code erp.approval.delegated.v1} (TASK-ERP-BE-013) — emitted on
     * DelegationGrant create. aggregateType = {@code DelegationGrant}, aggregateId
     * = partition key = grantId. Producer-only forward interface; the existing
     * consumers do not subscribe. {@code validTo} / {@code reason} ABSENT when
     * null (NON_NULL).
     */
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
        writeEvent(DELEGATION_AGGREGATE_TYPE, g.getId(), EVENT_APPROVAL_DELEGATED, SOURCE, p);
    }

    /**
     * NEW topic {@code erp.approval.delegation.revoked.v1} (TASK-ERP-BE-015) —
     * emitted when a DelegationGrant is **revoked** (an actual ACTIVE→REVOKED
     * transition; the application service does not call this on an idempotent
     * re-revoke). aggregateType = {@code DelegationGrant}, aggregateId = partition
     * key = grantId — same aggregate as {@link #publishDelegated} so a consumer
     * sees grant + revoke on one partition (per-grant ordering). Payload carries
     * the grant identity + actor; the validity window ({@code validFrom}/
     * {@code validTo}) is NOT in the revoke payload (a revoke does not restate the
     * window — the read model keeps what the {@code delegated} event projected).
     * {@code reason} ABSENT when null/blank (NON_NULL).
     */
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
                EVENT_APPROVAL_DELEGATION_REVOKED, SOURCE, p);
    }

    /**
     * Common approval payload (erp-approval-events.md § Payload schemas + v2.0
     * amendment). {@code finalizedAt} ABSENT on submitted; {@code reason} ABSENT
     * when none (a {@code null} map value is dropped — here we simply omit the
     * key). Additive v2.0 fields {@code currentStage} (0-based) + {@code
     * totalStages} are always present (existing consumers ignore unknown
     * properties). {@code approverId} = the relevant stage's approver (submitted
     * → stage 0; approved → the final stage — both = the denormalized current
     * {@code approverId} at emit time).
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

    /** Resolve the event type for a given finalized/submitted transition. */
    public static String eventTypeFor(ApprovalStatus transition) {
        return switch (transition) {
            case SUBMITTED -> EVENT_APPROVAL_SUBMITTED;
            case APPROVED -> EVENT_APPROVAL_APPROVED;
            case REJECTED -> EVENT_APPROVAL_REJECTED;
            case WITHDRAWN -> EVENT_APPROVAL_WITHDRAWN;
            default -> throw new IllegalArgumentException(
                    "no event for transition " + transition);
        };
    }
}
