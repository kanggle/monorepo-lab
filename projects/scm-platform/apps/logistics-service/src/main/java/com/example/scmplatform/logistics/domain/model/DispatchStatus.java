package com.example.scmplatform.logistics.domain.model;

/**
 * Status machine for {@link Dispatch} (ADR-053 §D2, scm S1).
 *
 * <pre>
 *   PENDING ──recordAck──────> DISPATCHED
 *      │                          ▲
 *      └─recordFailure─> DISPATCH_FAILED ──recordAck (operator :retry)──┘
 * </pre>
 *
 * {@code DISPATCHED} is the terminal success state; a re-dispatch of a {@code DISPATCHED}
 * shipment is an idempotent no-op (cached ack, no vendor call). {@code recordFailure} on a
 * {@code DISPATCHED} dispatch is an illegal transition and is rejected.
 */
public enum DispatchStatus {
    PENDING,
    DISPATCHED,
    DISPATCH_FAILED;

    /** A dispatch may be driven to the vendor from PENDING or from a prior DISPATCH_FAILED. */
    public boolean canDispatch() {
        return this == PENDING || this == DISPATCH_FAILED;
    }

    public boolean isDispatched() {
        return this == DISPATCHED;
    }
}
