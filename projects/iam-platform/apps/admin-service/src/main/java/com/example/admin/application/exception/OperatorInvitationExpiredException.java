package com.example.admin.application.exception;

/**
 * TASK-MONO-772 S3 — 410 {@code OPERATOR_INVITATION_EXPIRED}: the invitation's {@code expires_at} has passed. The inviter's resend
 * sends a new link (auth-to-admin.md § accept, step 3).
 */
public class OperatorInvitationExpiredException extends OperatorInvitationException {

    public OperatorInvitationExpiredException(String message) {
        super("OPERATOR_INVITATION_EXPIRED", 410, message);
    }
}
