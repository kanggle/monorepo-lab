package com.example.account.application.exception;

/**
 * TASK-MONO-772 S2 (admin-to-account.md § verified-email:match) — the account is an ACTIVE pool account, but its
 * email is not the one the caller expected → 403 {@code ACCOUNT_EMAIL_MISMATCH}. The message never quotes either
 * address (R4).
 */
public class AccountEmailMismatchException extends RuntimeException {

    public AccountEmailMismatchException(String message) {
        super(message);
    }
}
