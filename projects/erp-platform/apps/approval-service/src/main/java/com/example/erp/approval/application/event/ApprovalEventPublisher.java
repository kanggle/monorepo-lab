package com.example.erp.approval.application.event;

import com.example.erp.approval.domain.delegation.DelegationGrant;
import com.example.erp.approval.domain.request.ApprovalRequest;
import com.example.erp.approval.domain.request.ApprovalStatus;

/**
 * Outbox write-path port for approval-service domain events (TASK-ERP-BE-025 —
 * outbox v1 → v2). Every {@code publish*} call happens INSIDE the use-case
 * {@code @Transactional} boundary so the event write commits atomically with the
 * state change + audit row (erp E4 / A7 atomicity). partition key =
 * {@code approvalRequestId} (transition events) / {@code grantId} (delegation
 * events).
 *
 * <p>The v1 implementation extended {@code BaseEventPublisher} (lib
 * {@code OutboxWriter} → {@code outbox} table). The v2 implementation
 * {@link com.example.erp.approval.infrastructure.outbox.OutboxApprovalEventPublisher}
 * builds the canonical 7-field envelope and persists an {@code approval_outbox}
 * row; {@code ApprovalOutboxPublisher} relays it. The application layer + caller
 * unit tests are unchanged (they mock this port).
 *
 * <p>Contract: {@code specs/contracts/events/erp-approval-events.md}.
 */
public interface ApprovalEventPublisher {

    String AGGREGATE_TYPE = "ApprovalRequest";
    /** TASK-ERP-BE-013 — the delegated event's aggregate type. */
    String DELEGATION_AGGREGATE_TYPE = "DelegationGrant";

    String EVENT_APPROVAL_SUBMITTED = "erp.approval.submitted";
    String EVENT_APPROVAL_APPROVED = "erp.approval.approved";
    String EVENT_APPROVAL_REJECTED = "erp.approval.rejected";
    String EVENT_APPROVAL_WITHDRAWN = "erp.approval.withdrawn";
    /** TASK-ERP-BE-013 — new topic, emitted on DelegationGrant create. */
    String EVENT_APPROVAL_DELEGATED = "erp.approval.delegated";
    /** TASK-ERP-BE-015 — new topic, emitted on DelegationGrant revoke (ACTIVE→REVOKED). */
    String EVENT_APPROVAL_DELEGATION_REVOKED = "erp.approval.delegation.revoked";

    void publishSubmitted(ApprovalRequest r, String actor);

    /**
     * {@code actingForApproverId} (TASK-ERP-BE-013) = the stage approver A when a
     * delegate performed the approval on A's behalf; {@code null} (ABSENT) when the
     * approver acted themselves.
     */
    void publishApproved(ApprovalRequest r, String actor, String reason,
                         String actingForApproverId);

    void publishRejected(ApprovalRequest r, String actor, String reason,
                         String actingForApproverId);

    void publishWithdrawn(ApprovalRequest r, String actor, String reason);

    /**
     * NEW topic {@code erp.approval.delegated.v1} (TASK-ERP-BE-013) — emitted on
     * DelegationGrant create. aggregateType = {@code DelegationGrant}, aggregateId
     * = partition key = grantId.
     */
    void publishDelegated(DelegationGrant g, String actor);

    /**
     * NEW topic {@code erp.approval.delegation.revoked.v1} (TASK-ERP-BE-015) —
     * emitted when a DelegationGrant is **revoked** (an actual ACTIVE→REVOKED
     * transition). aggregateType = {@code DelegationGrant}, aggregateId = partition
     * key = grantId.
     */
    void publishRevoked(DelegationGrant g, String actor);

    /** Resolve the event type for a given finalized/submitted transition. */
    static String eventTypeFor(ApprovalStatus transition) {
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
