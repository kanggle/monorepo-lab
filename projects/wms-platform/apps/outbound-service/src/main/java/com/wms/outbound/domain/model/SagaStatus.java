package com.wms.outbound.domain.model;

/**
 * State enum for the {@code OutboundSaga} aggregate.
 *
 * <p>Authoritative reference:
 * {@code specs/services/outbound-service/state-machines/saga-status.md}.
 *
 * <p>Terminal states: {@link #COMPLETED}, {@link #CANCELLED},
 * {@link #RESERVATION_FAILED} (alias for {@code RESERVE_FAILED} in the spec).
 *
 * <p>{@link #SHIPPED_NOT_NOTIFIED} is a non-terminal alert state; saga remains
 * here until manual TMS retry succeeds.
 *
 * <p>{@link #COMPENSATION_FAILED} is reserved for future use when a release
 * step exhausts retries.
 */
public enum SagaStatus {
    REQUESTED,
    RESERVATION_PENDING,
    RESERVATION_FAILED,
    RESERVED,
    PICKING_CONFIRMED,
    PACKING_CONFIRMED,
    SHIPPED,
    SHIPPED_NOT_NOTIFIED,
    COMPLETED,
    CANCELLED,
    COMPENSATION_FAILED
}
