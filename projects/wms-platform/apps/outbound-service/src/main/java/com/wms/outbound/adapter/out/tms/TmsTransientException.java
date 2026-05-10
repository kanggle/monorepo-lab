package com.wms.outbound.adapter.out.tms;

/**
 * Adapter-internal marker for transient TMS failures (5xx, 429, IO,
 * timeout). Resilience4j's circuit breaker and retry are configured to
 * <em>record</em> and <em>retry</em> on this exception (per
 * {@code external-integrations.md} §2.5 / §2.6). After retry exhaustion the
 * adapter translates this to
 * {@link com.wms.outbound.domain.exception.ExternalServiceUnavailableException}.
 */
final class TmsTransientException extends RuntimeException {

    private final int httpStatus;

    TmsTransientException(String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    TmsTransientException(String message, int httpStatus, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    int httpStatus() {
        return httpStatus;
    }
}
