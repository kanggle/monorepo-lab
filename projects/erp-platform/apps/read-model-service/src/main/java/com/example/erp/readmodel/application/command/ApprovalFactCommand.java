package com.example.erp.readmodel.application.command;

import com.example.erp.readmodel.domain.approval.ApprovalStatus;
import com.example.erp.readmodel.domain.approval.ApprovalSubjectType;

import java.time.Instant;

/**
 * Parsed approval-fact command handed to {@link
 * com.example.erp.readmodel.application.ApplyApprovalFactUseCase} by a Kafka
 * consumer. Carries the dedupe / provenance metadata + the projected approval
 * fact fields (already extracted by the consumer's envelope parser — the
 * application layer touches no Kafka / Jackson type, E5 boundary).
 *
 * <p>{@code status} is derived from the topic/eventType ({@code SUBMITTED} for
 * {@code erp.approval.submitted.v1}; terminal otherwise). {@code submittedAt} is
 * non-null only on a {@code submitted} event; {@code finalizedAt} / {@code reason}
 * are non-null only on a terminal event (ABSENT-or-present per the contract).
 */
public record ApprovalFactCommand(
        String eventId,
        String topic,
        String approvalRequestId,
        ApprovalStatus status,
        ApprovalSubjectType subjectType,
        String subjectId,
        String approverId,
        String submitterId,
        Instant occurredAt,
        Instant submittedAt,
        Instant finalizedAt,
        String reason
) {

    public boolean isSubmitted() {
        return status == ApprovalStatus.SUBMITTED;
    }
}
