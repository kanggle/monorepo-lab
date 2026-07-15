package com.example.auth.application.exception;

public class LoginRateLimitedException extends RuntimeException {

    private final long retryAfterSeconds;

    /**
     * @param retryAfterSeconds seconds until the per-email failure-count window
     *                          resets (TASK-BE-512), surfaced as the {@code Retry-After}
     *                          header on the 429 response. Always positive —
     *                          see {@link com.example.auth.domain.repository.LoginAttemptCounter#getTtlSeconds}.
     */
    public LoginRateLimitedException(long retryAfterSeconds) {
        super("Too many login attempts. Try again later.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
