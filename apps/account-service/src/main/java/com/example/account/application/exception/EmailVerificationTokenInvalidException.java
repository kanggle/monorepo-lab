package com.example.account.application.exception;

/**
 * Thrown by {@code VerifyEmailUseCase} when the supplied verification token
 * cannot be honoured (TASK-BE-114).
 *
 * <p>The same exception covers all "token cannot be consumed" cases — missing,
 * expired, already-used, or pointing at an account that no longer exists — so
 * the API does not leak which of those conditions tripped.</p>
 *
 * <p>Mapped to HTTP 400 {@code TOKEN_EXPIRED_OR_INVALID} by
 * {@link com.example.account.presentation.advice.GlobalExceptionHandler}.</p>
 */
public class EmailVerificationTokenInvalidException extends RuntimeException {

    public EmailVerificationTokenInvalidException() {
        super("Email verification token is invalid or has expired");
    }
}
