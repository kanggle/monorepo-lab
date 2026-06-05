package com.example.erp.notification.domain.notification;

/**
 * The notification type fanned out from an approval event (1:1 with the consumed
 * {@code eventType}). architecture.md § Recipient resolution.
 */
public enum NotificationType {

    /** {@code erp.approval.submitted.v1} → notify the approver. */
    APPROVAL_SUBMITTED,
    /** {@code erp.approval.approved.v1} → notify the submitter. */
    APPROVAL_APPROVED,
    /** {@code erp.approval.rejected.v1} → notify the submitter. */
    APPROVAL_REJECTED,
    /** {@code erp.approval.withdrawn.v1} → notify the approver. */
    APPROVAL_WITHDRAWN,
    /**
     * {@code erp.approval.delegated.v1} → notify the delegate (TASK-ERP-BE-014).
     * The delegation event has a different aggregate / payload shape from the four
     * transition events; it is mapped via {@code DelegationEvent}, not
     * {@code ApprovalEvent}.
     */
    DELEGATION_GRANTED
}
