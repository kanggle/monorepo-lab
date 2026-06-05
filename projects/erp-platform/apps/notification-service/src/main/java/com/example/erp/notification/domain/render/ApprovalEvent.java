package com.example.erp.notification.domain.render;

import com.example.erp.notification.domain.notification.NotificationType;

import java.util.Objects;
import java.util.Optional;

/**
 * Pure, validated approval transition the notification domain operates on
 * (mapped from the Kafka envelope by the inbound adapter; no Kafka / Jackson
 * types reach the domain). Carries the ids + the optional terminal fields the
 * renderer composes a message from. The producer payload is consumed unchanged
 * (erp-approval-events.md § Payload schemas).
 */
public record ApprovalEvent(
        String eventId,
        NotificationType type,
        String tenantId,
        String approvalRequestId,
        String subjectType,
        String subjectId,
        String approverId,
        String submitterId,
        String finalizedAt,
        String reason) {

    public ApprovalEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(approvalRequestId, "approvalRequestId");
        Objects.requireNonNull(approverId, "approverId");
        Objects.requireNonNull(submitterId, "submitterId");
    }

    public Optional<String> finalizedAtOpt() {
        return Optional.ofNullable(finalizedAt);
    }

    public Optional<String> reasonOpt() {
        return Optional.ofNullable(reason);
    }
}
