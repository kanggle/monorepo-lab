package com.wms.outbound.domain.model;

import com.wms.outbound.domain.exception.StateTransitionInvalidException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Saga-state aggregate (pure POJO — no JPA annotations).
 *
 * <p>Authoritative references:
 * {@code specs/services/outbound-service/domain-model.md} §6 and
 * {@code specs/services/outbound-service/state-machines/saga-status.md}.
 *
 * <p>Transitions follow the saga state machine. Re-delivery / out-of-order
 * messages from {@code inventory-service} are handled by the
 * {@link com.wms.outbound.application.saga.OutboundSagaCoordinator}; this
 * aggregate enforces invariants only — illegal transitions raise
 * {@link StateTransitionInvalidException}.
 *
 * <p>The {@code pickingRequestId} field is the cross-service correlation key:
 * it equals the saga id in TASK-BE-037 (no PickingRequest aggregate yet) and
 * will be set to {@code PickingRequest.id} once that aggregate ships in
 * TASK-BE-038. Inventory replies carry it as their {@code pickingRequestId}
 * field, which is how the coordinator locates the right saga.
 */
public final class OutboundSaga {

    private final UUID sagaId;
    private final UUID orderId;
    private SagaStatus status;
    private UUID pickingRequestId;
    private String failureReason;
    private final Instant startedAt;
    private Instant lastTransitionAt;
    private long version;

    public OutboundSaga(UUID sagaId,
                        UUID orderId,
                        SagaStatus status,
                        UUID pickingRequestId,
                        String failureReason,
                        Instant startedAt,
                        Instant lastTransitionAt,
                        long version) {
        this.sagaId = Objects.requireNonNull(sagaId, "sagaId");
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.status = Objects.requireNonNull(status, "status");
        this.pickingRequestId = pickingRequestId;
        this.failureReason = failureReason;
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.lastTransitionAt = Objects.requireNonNull(lastTransitionAt, "lastTransitionAt");
        this.version = version;
    }

    /**
     * Convenience factory for newly-created sagas: {@code REQUESTED} state,
     * {@code pickingRequestId} bootstrapped to the saga id (TASK-BE-037 only;
     * once TASK-BE-038 ships, this becomes a real {@code PickingRequest.id}).
     */
    public static OutboundSaga newRequested(UUID sagaId, UUID orderId, Instant now) {
        return new OutboundSaga(sagaId, orderId, SagaStatus.REQUESTED,
                sagaId /* pickingRequestId == sagaId in v1 scope */,
                null, now, now, 0L);
    }

    /**
     * Saga step 1 success: {@code REQUESTED → RESERVED}.
     *
     * <p>Re-delivered {@code inventory.reserved} for an already-{@code RESERVED}
     * saga is a no-op (consumer-level eventId dedupe + state-machine reject).
     */
    public void onInventoryReserved(Instant now) {
        if (status == SagaStatus.RESERVED) {
            return;
        }
        if (status != SagaStatus.REQUESTED) {
            throw new StateTransitionInvalidException(status.name(), SagaStatus.RESERVED.name());
        }
        this.status = SagaStatus.RESERVED;
        this.lastTransitionAt = now;
    }

    /**
     * Reserve-failed compensation: {@code REQUESTED → RESERVE_FAILED}.
     */
    public void onReserveFailed(String reason, Instant now) {
        if (status == SagaStatus.RESERVE_FAILED) {
            return;
        }
        if (status != SagaStatus.REQUESTED) {
            throw new StateTransitionInvalidException(status.name(), SagaStatus.RESERVE_FAILED.name());
        }
        this.status = SagaStatus.RESERVE_FAILED;
        this.failureReason = reason;
        this.lastTransitionAt = now;
    }

    /**
     * Cancellation released by inventory: {@code CANCELLATION_REQUESTED → CANCELLED}.
     */
    public void onInventoryReleased(Instant now) {
        if (status == SagaStatus.CANCELLED) {
            return;
        }
        if (status != SagaStatus.CANCELLATION_REQUESTED) {
            throw new StateTransitionInvalidException(status.name(), SagaStatus.CANCELLED.name());
        }
        this.status = SagaStatus.CANCELLED;
        this.lastTransitionAt = now;
    }

    /**
     * Final saga step: {@code SHIPPED → COMPLETED}.
     */
    public void onInventoryConfirmed(Instant now) {
        if (status == SagaStatus.COMPLETED) {
            return;
        }
        if (status != SagaStatus.SHIPPED) {
            throw new StateTransitionInvalidException(status.name(), SagaStatus.COMPLETED.name());
        }
        this.status = SagaStatus.COMPLETED;
        this.lastTransitionAt = now;
    }

    /**
     * User-issued cancellation: any of
     * {@code REQUESTED}, {@code RESERVED}, {@code PICKING_CONFIRMED},
     * {@code PACKING_CONFIRMED} → {@code CANCELLATION_REQUESTED}.
     *
     * <p>If the saga is in {@code REQUESTED} (no reservation yet), the
     * coordinator may transition straight to {@link SagaStatus#CANCELLED}
     * without waiting for {@code inventory.released}. That decision is in
     * the coordinator — this method only enforces invariants.
     */
    public void requestCancellation(Instant now) {
        if (status == SagaStatus.CANCELLATION_REQUESTED || status == SagaStatus.CANCELLED) {
            return;
        }
        switch (status) {
            case REQUESTED, RESERVED, PICKING_CONFIRMED, PACKING_CONFIRMED -> {
                this.status = SagaStatus.CANCELLATION_REQUESTED;
                this.lastTransitionAt = now;
            }
            default -> throw new StateTransitionInvalidException(status.name(),
                    SagaStatus.CANCELLATION_REQUESTED.name());
        }
    }

    /**
     * Pre-pick cancellation completes immediately because no reservation
     * was created. {@code REQUESTED | CANCELLATION_REQUESTED → CANCELLED}.
     */
    public void cancelImmediately(Instant now) {
        if (status == SagaStatus.CANCELLED) {
            return;
        }
        if (status != SagaStatus.REQUESTED && status != SagaStatus.CANCELLATION_REQUESTED) {
            throw new StateTransitionInvalidException(status.name(), SagaStatus.CANCELLED.name());
        }
        this.status = SagaStatus.CANCELLED;
        this.lastTransitionAt = now;
    }

    /**
     * Saga step 4 fired: {@code PACKING_CONFIRMED → SHIPPED}.
     * (Used by ConfirmShippingUseCase in TASK-BE-038; included here so the
     * state machine is complete for the consumers that already exist.)
     */
    public void onShippingConfirmed(Instant now) {
        if (status == SagaStatus.SHIPPED || status == SagaStatus.COMPLETED) {
            return;
        }
        if (status != SagaStatus.PACKING_CONFIRMED) {
            throw new StateTransitionInvalidException(status.name(), SagaStatus.SHIPPED.name());
        }
        this.status = SagaStatus.SHIPPED;
        this.lastTransitionAt = now;
    }

    public UUID sagaId() {
        return sagaId;
    }

    public UUID orderId() {
        return orderId;
    }

    public SagaStatus status() {
        return status;
    }

    public UUID pickingRequestId() {
        return pickingRequestId;
    }

    public String failureReason() {
        return failureReason;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant lastTransitionAt() {
        return lastTransitionAt;
    }

    public long version() {
        return version;
    }
}
