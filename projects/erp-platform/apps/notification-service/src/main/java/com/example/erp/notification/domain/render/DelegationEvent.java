package com.example.erp.notification.domain.render;

import com.example.erp.notification.domain.notification.NotificationType;

import java.util.Objects;
import java.util.Optional;

/**
 * Pure, validated delegation-granted event the notification domain operates on
 * (TASK-ERP-BE-014; mapped from the {@code erp.approval.delegated.v1} envelope by
 * the inbound adapter — no Kafka / Jackson types reach the domain). This is the
 * parallel render model to {@link ApprovalEvent}: the delegation event carries a
 * different payload shape (no {@code approverId} / {@code submitterId} /
 * {@code subjectId}; {@code aggregateType = DelegationGrant}). The producer
 * payload is consumed unchanged (erp-approval-events.md § v2.1 amendment).
 *
 * <p>The notification {@link #type()} is always {@link NotificationType#DELEGATION_GRANTED};
 * the recipient is the {@link #delegateId()} (the employee who received the
 * delegation authority).
 */
public record DelegationEvent(
        String eventId,
        String tenantId,
        String grantId,
        String delegatorId,
        String delegateId,
        String validFrom,
        String validTo,
        String reason) {

    public DelegationEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(grantId, "grantId");
        Objects.requireNonNull(delegatorId, "delegatorId");
        Objects.requireNonNull(delegateId, "delegateId");
        Objects.requireNonNull(validFrom, "validFrom");
    }

    /** Always {@link NotificationType#DELEGATION_GRANTED} for a delegation event. */
    public NotificationType type() {
        return NotificationType.DELEGATION_GRANTED;
    }

    public Optional<String> validToOpt() {
        return Optional.ofNullable(validTo);
    }

    public Optional<String> reasonOpt() {
        return Optional.ofNullable(reason);
    }
}
