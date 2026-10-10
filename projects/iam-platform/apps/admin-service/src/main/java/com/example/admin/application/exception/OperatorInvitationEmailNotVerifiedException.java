package com.example.admin.application.exception;

/**
 * TASK-MONO-772 S3 — 403 {@code EMAIL_NOT_VERIFIED}: the email matches but is not verified — TASK-MONO-770's shared name, passed
 * through unchanged (step 4). The same invitation works once the person verifies.
 */
public class OperatorInvitationEmailNotVerifiedException extends OperatorInvitationException {

    public OperatorInvitationEmailNotVerifiedException(String message) {
        super("EMAIL_NOT_VERIFIED", 403, message);
    }
}
