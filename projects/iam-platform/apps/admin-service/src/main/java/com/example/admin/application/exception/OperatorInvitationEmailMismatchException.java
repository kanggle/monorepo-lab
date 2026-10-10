package com.example.admin.application.exception;

/**
 * TASK-MONO-772 S3 — 403 {@code OPERATOR_INVITATION_EMAIL_MISMATCH}: the signed-in pool account's email is not the invited address
 * (account-service answered {@code ACCOUNT_EMAIL_MISMATCH} — step 4).
 */
public class OperatorInvitationEmailMismatchException extends OperatorInvitationException {

    public OperatorInvitationEmailMismatchException(String message) {
        super("OPERATOR_INVITATION_EMAIL_MISMATCH", 403, message);
    }
}
