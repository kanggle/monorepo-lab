package com.example.account.application.exception;

/**
 * Thrown when a verify-email or resend-verification-email request targets an
 * account whose email is already verified (TASK-BE-114).
 *
 * <p>The verify path raises this when the domain's
 * {@link com.example.account.domain.account.Account#verifyEmail(java.time.Instant)}
 * throws {@link IllegalStateException}; the resend path raises it pre-flight
 * to avoid issuing a token that could not be consumed.</p>
 *
 * <p>Mapped to HTTP 409 {@code EMAIL_ALREADY_VERIFIED} by
 * {@link com.example.account.presentation.advice.GlobalExceptionHandler}.</p>
 */
public class EmailAlreadyVerifiedException extends RuntimeException {

    public EmailAlreadyVerifiedException() {
        super("Email is already verified");
    }
}
