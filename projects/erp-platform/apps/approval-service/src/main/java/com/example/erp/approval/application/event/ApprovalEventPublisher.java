package com.example.erp.approval.application.event;

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

    public static final String EVENT_APPROVAL_SUBMITTED = "erp.approval.submitted";
    public static final String EVENT_APPROVAL_APPROVED = "erp.approval.approved";
    public static final String EVENT_APPROVAL_REJECTED = "erp.approval.rejected";
    public static final String EVENT_APPROVAL_WITHDRAWN = "erp.approval.withdrawn";

    public ApprovalEventPublisher(OutboxWriter outboxWriter, ObjectMapper objectMapper) {
        super(outboxWriter, objectMapper);
    }

    public void publishSubmitted(ApprovalRequest r, String actor) {
        writeEvent(AGGREGATE_TYPE, r.getId(), EVENT_APPROVAL_SUBMITTED, SOURCE,
                payload(r, actor, null, null));
    }

    public void publishApproved(ApprovalRequest r, String actor, String reason) {
        writeEvent(AGGREGATE_TYPE, r.getId(), EVENT_APPROVAL_APPROVED, SOURCE,
                payload(r, actor, r.getFinalizedAt(), reason));
    }

    public void publishRejected(ApprovalRequest r, String actor, String reason) {
        writeEvent(AGGREGATE_TYPE, r.getId(), EVENT_APPROVAL_REJECTED, SOURCE,
                payload(r, actor, r.getFinalizedAt(), reason));
    }

    public void publishWithdrawn(ApprovalRequest r, String actor, String reason) {
        writeEvent(AGGREGATE_TYPE, r.getId(), EVENT_APPROVAL_WITHDRAWN, SOURCE,
                payload(r, actor, r.getFinalizedAt(), reason));
    }

    /**
     * Common approval payload (erp-approval-events.md § Payload schemas).
     * {@code finalizedAt} ABSENT on submitted; {@code reason} ABSENT when none
     * (a {@code null} map value is dropped by the NON_NULL serialization the
     * envelope ObjectMapper applies — here we simply omit the key).
     */
    private static Map<String, Object> payload(ApprovalRequest r, String actor,
                                               Instant finalizedAt, String reason) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("approvalRequestId", r.getId());
        p.put("subjectType", r.getSubjectType().name());
        p.put("subjectId", r.getSubjectId());
        p.put("approverId", r.getApproverId());
        p.put("submitterId", r.getSubmitterId());
        p.put("tenantId", r.getTenantId());
        p.put("occurredAt", Instant.now().toString());
        p.put("actor", actor);
        if (finalizedAt != null) {
            p.put("finalizedAt", finalizedAt.toString());
        }
        if (reason != null && !reason.isBlank()) {
            p.put("reason", reason);
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
