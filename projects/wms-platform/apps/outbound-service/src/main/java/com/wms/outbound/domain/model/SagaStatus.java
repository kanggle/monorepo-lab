package com.wms.outbound.domain.model;

/**
 * State enum for the {@code OutboundSaga} aggregate.
 *
 * <p>Authoritative reference:
 * {@code specs/services/outbound-service/state-machines/saga-status.md} and
 * {@code specs/services/outbound-service/domain-model.md} §6.
 *
 * <p>Terminal states: {@link #COMPLETED}, {@link #CANCELLED},
 * {@link #RESERVE_FAILED}.
 *
 * <p>{@link #SHIPPED_NOT_NOTIFIED} is a non-terminal alert state — saga
 * remains here until manual TMS retry succeeds.
 *
 * <p>{@link #CANCELLATION_REQUESTED} is the transient state set when an
 * order is cancelled while inventory still holds an active reservation.
 * The saga awaits {@code inventory.released} before settling on
 * {@link #CANCELLED}.
 *
 * <p>The legacy {@code RESERVATION_PENDING} / {@code RESERVATION_FAILED} /
 * {@code COMPENSATION_FAILED} values from the bootstrap stub are removed
 * here because they did not appear in any spec; the saga state machine
 * defined in the spec is authoritative.
 */
public enum SagaStatus {
    REQUESTED,
    RESERVE_FAILED,
    RESERVED,
    PICKING_CONFIRMED,
    PACKING_CONFIRMED,
    CANCELLATION_REQUESTED,
    CANCELLED,
    SHIPPED,
    SHIPPED_NOT_NOTIFIED,
    COMPLETED
}
