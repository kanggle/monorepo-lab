package com.example.erp.notification.domain.recipient;

import com.example.erp.notification.domain.render.ApprovalEvent;
import com.example.erp.notification.domain.render.DelegationEvent;

/**
 * Resolves each approval transition to exactly ONE recipient employee id — the
 * notification logic that makes this service NOT a pure read-model. Pure module
 * (no framework, no outbound call: both ids are on every payload).
 *
 * <p>Mapping (architecture.md § Recipient resolution):
 * <ul>
 *   <li>{@code APPROVAL_SUBMITTED} → {@code approverId} — the request just
 *       arrived in the approver's queue.</li>
 *   <li>{@code APPROVAL_APPROVED} → {@code submitterId} — the requester is told
 *       the outcome.</li>
 *   <li>{@code APPROVAL_REJECTED} → {@code submitterId} — the requester is told
 *       the outcome.</li>
 *   <li>{@code APPROVAL_WITHDRAWN} → {@code approverId} — the submitter withdrew
 *       their OWN request (they already know), so the approver who had it
 *       <b>pending</b> is the one told it is no longer awaiting their action.</li>
 * </ul>
 */
public final class RecipientResolver {

    /** Resolves the single recipient for the event's type. */
    public Recipient resolve(ApprovalEvent event) {
        return switch (event.type()) {
            case APPROVAL_SUBMITTED, APPROVAL_WITHDRAWN -> new Recipient(event.approverId());
            case APPROVAL_APPROVED, APPROVAL_REJECTED -> new Recipient(event.submitterId());
            case DELEGATION_GRANTED -> throw new IllegalStateException(
                    "DELEGATION_GRANTED is not an approval-transition event (use the DelegationEvent overload)");
        };
    }

    /**
     * Resolves a delegation-granted event to the **delegate** — the employee who
     * received the delegation authority (TASK-ERP-BE-014). Pure pick from the
     * payload id; no outbound call.
     */
    public Recipient resolve(DelegationEvent event) {
        return new Recipient(event.delegateId());
    }
}
