package com.example.scmplatform.logistics.domain.error;

/**
 * Raised when a {@code Dispatch} status transition is not permitted from the current
 * state — e.g. recording a failure on an already-{@code DISPATCHED} shipment (S1). Maps to
 * HTTP 409 {@code DISPATCH_ALREADY_COMPLETED} at the web edge.
 */
public class IllegalDispatchTransitionException extends RuntimeException {

    public IllegalDispatchTransitionException(String message) {
        super(message);
    }
}
