package com.example.admin.application.exception;

/**
 * TASK-MONO-735: account-service answered 404 {@code ACCOUNT_NOT_FOUND} to an operator
 * lock/unlock — the target does not exist, or lives in a tenant other than the one the
 * operator is confined to (BE-467, enumeration-safe). Surfaced to the operator as
 * 404 {@code ACCOUNT_NOT_FOUND}, as admin-api.md already specified.
 *
 * <p>Before MONO-735 this answer reached the operator as 503 {@code DOWNSTREAM_ERROR}: a
 * definitive "no such account" dressed as "try again", and the console's confirm dialog
 * re-sent the SAME Idempotency-Key, which then died on the audit row's unique key as a 500.
 */
public class TargetAccountNotFoundException extends AccountBusinessException {
    public TargetAccountNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
