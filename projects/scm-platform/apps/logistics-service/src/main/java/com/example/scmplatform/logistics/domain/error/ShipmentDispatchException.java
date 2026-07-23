package com.example.scmplatform.logistics.domain.error;

/**
 * Thrown by a {@code ShipmentDispatchPort} implementation when the vendor dispatch fails —
 * transient exhaustion (retry/circuit/bulkhead), timeout/IO, or a permanent vendor 4xx. It
 * is a <b>domain</b> failure (framework-free): the vendor SDK / HTTP exception never crosses
 * the port boundary (I7/I8). The use case catches it and records {@code DISPATCH_FAILED}
 * (never a consume failure, S5).
 */
public class ShipmentDispatchException extends RuntimeException {

    private final boolean retryable;

    public ShipmentDispatchException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    /**
     * Whether the underlying failure was transient (5xx / 429 / timeout / IO / circuit-open /
     * bulkhead-full) rather than a permanent vendor 4xx. Recorded for the operator; both
     * outcomes land as {@code DISPATCH_FAILED} and recover via {@code :retry}.
     */
    public boolean isRetryable() {
        return retryable;
    }
}
