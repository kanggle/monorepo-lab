package com.example.erp.notification.domain.render;

import com.example.erp.notification.domain.notification.NotificationType;

import java.util.Objects;
import java.util.Optional;

/**
 * Pure, validated delegation-revoked event the notification domain operates on
 * (TASK-ERP-BE-016; mapped from the {@code erp.approval.delegation.revoked.v1}
 * envelope by the inbound adapter — no Kafka / Jackson types reach the domain).
 * The revoke paylod is the grant-fact minus the validity window
 * (erp-approval-events.md § v2.2 amendment): {@code grantId} / {@code delegatorId}
 * / {@code delegateId} / {@code reason?}. This is a separate render model from
 * {@link DelegationEvent} (which requires {@code validFrom}); the four transition
 * consumers + the delegated path stay unchanged.
 *
 * <p>The notification {@link #type()} is always
 * {@link NotificationType#DELEGATION_REVOKED}; the recipient is the
 * {@link #delegateId()} (the employee who LOSES the delegated authority).
 */
public record DelegationRevokedEvent(
        String eventId,
        String tenantId,
        String grantId,
        String delegatorId,
        String delegateId,
        String reason) {

    public DelegationRevokedEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(grantId, "grantId");
        Objects.requireNonNull(delegatorId, "delegatorId");
        Objects.requireNonNull(delegateId, "delegateId");
    }

    /** Always {@link NotificationType#DELEGATION_REVOKED} for a revoke event. */
    public NotificationType type() {
        return NotificationType.DELEGATION_REVOKED;
    }

    public Optional<String> reasonOpt() {
        return Optional.ofNullable(reason);
    }
}
