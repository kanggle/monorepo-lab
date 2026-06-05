package com.example.erp.readmodel.application.command;

import com.example.erp.readmodel.domain.delegation.DelegationFactStatus;

import java.time.Instant;

/**
 * Parsed delegation-fact command handed to {@link
 * com.example.erp.readmodel.application.ApplyDelegationFactUseCase} by a Kafka
 * consumer (TASK-ERP-BE-015). Carries the dedupe / provenance metadata + the
 * projected delegation fact fields (already extracted by the consumer's envelope
 * mapper — the application layer touches no Kafka / Jackson type, E5 boundary).
 *
 * <p>{@code status} is derived from the topic ({@code GRANTED} for
 * {@code erp.approval.delegated.v1}; {@code REVOKED} for
 * {@code erp.approval.delegation.revoked.v1}). {@code validFrom}/{@code validTo}
 * are non-null only on a {@code delegated} event (the revoke payload carries no
 * validity window). {@code revokedAt} is the {@code occurredAt} of a revoke event.
 *
 * <p>{@code scope} ({@code GLOBAL}|{@code REQUEST}) + {@code scopeRequestId} are the
 * grant-time scoping (TASK-ERP-BE-018); they are non-null only on a {@code delegated}
 * event (a revoke restates neither), and {@code scopeRequestId} is non-null only when
 * {@code scope == REQUEST}.
 */
public record DelegationFactCommand(
        String eventId,
        String topic,
        String grantId,
        DelegationFactStatus status,
        String delegatorId,
        String delegateId,
        Instant validFrom,
        Instant validTo,
        String reason,
        Instant occurredAt,
        Instant revokedAt,
        String scope,
        String scopeRequestId
) {

    public boolean isGranted() {
        return status == DelegationFactStatus.ACTIVE;
    }
}
