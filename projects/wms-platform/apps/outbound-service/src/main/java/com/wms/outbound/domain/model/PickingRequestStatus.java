package com.wms.outbound.domain.model;

/**
 * State enum for {@link PickingRequest}.
 *
 * <p>Authoritative reference:
 * {@code specs/services/outbound-service/domain-model.md} §2.
 *
 * <p>Status transitions are driven solely by the
 * {@link com.wms.outbound.application.saga.OutboundSagaCoordinator}; direct
 * writes are forbidden (T4).
 */
public enum PickingRequestStatus {
    /** Outbox row written; awaiting {@code inventory.reserved}. */
    PENDING,
    /** Inventory acknowledged the reservation. */
    SUBMITTED,
    /** Inventory rejected with {@code INSUFFICIENT_STOCK}. */
    RESERVE_FAILED
}
