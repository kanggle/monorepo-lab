package com.wms.outbound.adapter.out.tms;

/**
 * Adapter-internal marker for permanent TMS failures (4xx ≠ 429). Per
 * {@code external-integrations.md} §2.11, these are caller-side bugs (bad
 * payload, expired token, missing account) and must NOT trigger a retry
 * (I3). Resilience4j's retry / circuit breaker are configured to
 * <em>ignore</em> this exception. The adapter translates it to
 * {@link com.wms.outbound.domain.exception.ExternalServiceUnavailableException}
 * for the saga handler, which transitions the saga to
 * {@code SHIPPED_NOT_NOTIFIED} with a populated {@code failureReason}.
 */
final class TmsPermanentException extends RuntimeException {

    private final int httpStatus;

    TmsPermanentException(String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    int httpStatus() {
        return httpStatus;
    }
}
