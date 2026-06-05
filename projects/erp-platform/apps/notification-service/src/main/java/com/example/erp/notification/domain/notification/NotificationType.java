package com.example.erp.notification.domain.notification;

/**
 * The notification type fanned out from an approval transition (1:1 with the
 * consumed {@code eventType}). architecture.md § Recipient resolution.
 */
public enum NotificationType {

    /** {@code erp.approval.submitted.v1} → notify the approver. */
    APPROVAL_SUBMITTED,
    /** {@code erp.approval.approved.v1} → notify the submitter. */
    APPROVAL_APPROVED,
    /** {@code erp.approval.rejected.v1} → notify the submitter. */
    APPROVAL_REJECTED,
    /** {@code erp.approval.withdrawn.v1} → notify the approver. */
    APPROVAL_WITHDRAWN
}
