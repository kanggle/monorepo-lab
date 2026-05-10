package com.wms.outbound.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code outbound.alert.saga.recovery.exhausted} — emitted by the saga
 * sweeper (TASK-BE-050) when a saga has been re-emitted the configured
 * maximum number of times without advancing. Co-committed (T3) with the
 * saga's transition to {@link com.wms.outbound.domain.model.SagaStatus#STUCK_RECOVERY_FAILED}.
 *
 * <p>Schema: {@code specs/contracts/events/admin-events.md} §A1
 * "outbound.alert.saga.recovery.exhausted".
 *
 * <p>Topic: {@code wms.outbound.alert.saga.recovery.v1} —
 * the alert lives in the admin-events catalog because it exists primarily
 * for the admin alert dashboard and notification-service escalation.
 */
public record SagaRecoveryExhaustedEvent(
        UUID sagaId,
        UUID orderId,
        String stuckState,
        int reEmitCount,
        Instant lastTransitionAt,
        String failureReason,
        Instant exhaustedAt,
        Instant occurredAt,
        String actorId
) implements OutboundDomainEvent {

    @Override
    public UUID aggregateId() {
        return sagaId;
    }

    @Override
    public String aggregateType() {
        return "outbound_saga";
    }

    @Override
    public String eventType() {
        return "outbound.alert.saga.recovery.exhausted";
    }

    @Override
    public String partitionKey() {
        return sagaId.toString();
    }
}
