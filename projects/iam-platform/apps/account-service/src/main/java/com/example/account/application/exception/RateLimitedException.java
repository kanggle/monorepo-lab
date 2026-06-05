package com.example.account.application.exception;

/**
 * Thrown when an action is rejected because the caller has exceeded the
 * documented rate limit (TASK-BE-114).
 *
 * <p>Currently used by the email verification resend flow: a successful resend
 * sets a 5-minute marker in Redis, and subsequent resend attempts within that
 * window throw this exception.</p>
 *
 * <p>Mapped to HTTP 429 {@code RATE_LIMITED} by
 * {@link com.example.account.presentation.advice.GlobalExceptionHandler}.</p>
 */
public class RateLimitedException extends RuntimeException {

    public RateLimitedException(String message) {
        super(message);
    }
}
