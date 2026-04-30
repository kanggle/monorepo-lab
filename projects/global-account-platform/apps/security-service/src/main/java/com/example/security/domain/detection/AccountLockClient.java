package com.example.security.domain.detection;

import com.example.security.domain.suspicious.SuspiciousEvent;

/**
 * Port for the internal auto-lock HTTP call to account-service. The
 * implementation must set {@code Idempotency-Key} to
 * {@link SuspiciousEvent#getId()} and enforce 3 retries on 5xx / timeout
 * (never on 409).
 */
public interface AccountLockClient {

    /**
     * Request auto-lock. Returns a {@link LockResult} that describes the terminal
     * outcome — never throws for business-level failures (404, 409, 5xx after
     * retries). Unexpected protocol errors are propagated as runtime exceptions.
     */
    LockResult lock(SuspiciousEvent event);

    enum Status {
        SUCCESS,
        ALREADY_LOCKED,
        INVALID_TRANSITION, // 409 — deleted account, do not retry
        FAILURE             // exhausted retries, connection refused, 5xx
    }

    record LockResult(Status status, int httpStatus, String message) {}
}
