package com.example.account.domain.account;

/**
 * TASK-BE-473: raised by {@link PasswordPolicy} when a signup password fails the complexity
 * rules. Mapped to HTTP 422 {@code VALIDATION_ERROR} by
 * {@code com.example.account.presentation.advice.GlobalExceptionHandler}
 * ({@code specs/contracts/http/account-api.md} — {@code POST /api/accounts/signup}).
 */
public class PasswordPolicyViolationException extends RuntimeException {

    public PasswordPolicyViolationException(String message) {
        super(message);
    }
}
