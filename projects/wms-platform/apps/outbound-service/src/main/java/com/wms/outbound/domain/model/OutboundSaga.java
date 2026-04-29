package com.wms.outbound.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Saga-state aggregate (pure POJO — no JPA annotations).
 *
 * <p>Authoritative reference:
 * {@code specs/services/outbound-service/domain-model.md} §6 and
 * {@code specs/services/outbound-service/state-machines/saga-status.md}.
 *
 * <p><b>TASK-BE-034 stub:</b> the aggregate carries fields and identity but
 * the state-transition methods throw {@link UnsupportedOperationException}.
 * Real implementations land in TASK-BE-036 alongside the saga-step consumers.
 */
public final class OutboundSaga {

    private final UUID sagaId;
    private final UUID orderId;
    private SagaStatus status;
    private String failureReason;
    private final Instant startedAt;
    private Instant lastTransitionAt;
    private long version;

    public OutboundSaga(UUID sagaId,
                        UUID orderId,
                        SagaStatus status,
                        String failureReason,
                        Instant startedAt,
                        Instant lastTransitionAt,
                        long version) {
        this.sagaId = Objects.requireNonNull(sagaId, "sagaId");
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.status = Objects.requireNonNull(status, "status");
        this.failureReason = failureReason;
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.lastTransitionAt = Objects.requireNonNull(lastTransitionAt, "lastTransitionAt");
        this.version = version;
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

    /**
     * Stub — real transition logic lands in TASK-BE-036.
     */
    public void onInventoryReserved() {
        throw new UnsupportedOperationException("not yet implemented: TASK-BE-036");
    }

    /**
     * Stub — real compensation transition lands in TASK-BE-036.
     */
    public void onInventoryReleased() {
        throw new UnsupportedOperationException("not yet implemented: TASK-BE-036");
    }

    /**
     * Stub — real terminal transition lands in TASK-BE-036.
     */
    public void onInventoryConfirmed() {
        throw new UnsupportedOperationException("not yet implemented: TASK-BE-036");
    }

    /**
     * Stub — real reserve-failed transition lands in TASK-BE-036.
     */
    public void onReserveFailed() {
        throw new UnsupportedOperationException("not yet implemented: TASK-BE-036");
    }
}
