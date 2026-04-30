package com.example.auth.application.exception;

/**
 * Thrown by {@code ConfirmPasswordResetUseCase} when the supplied reset token
 * cannot be honoured.
 *
 * <p>The same exception covers all "token cannot be consumed" cases — missing,
 * expired, already-used, or pointing at an account whose credential row has
 * since been removed — so the API does not leak which of those conditions
 * tripped (TASK-BE-109 spec: "토큰 없음·만료·이미 사용됨 모두 동일 예외로 처리").</p>
 *
 * <p>Mapped to HTTP 400 {@code PASSWORD_RESET_TOKEN_INVALID} by
 * {@link com.example.auth.presentation.exception.AuthExceptionHandler}.</p>
 */
public class PasswordResetTokenInvalidException extends RuntimeException {

    public PasswordResetTokenInvalidException() {
        super("Password reset token is invalid or has expired");
    }
}
