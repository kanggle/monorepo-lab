package com.example.admin.application.exception;

/**
 * TASK-MONO-772 — 409 {@code OPERATOR_INVITATION_NOT_PENDING}: cancel / resend on an invitation that is
 * {@code ACCEPTED} (or {@code CANCELLED}, for resend), or the conditional update lost to a concurrent writer.
 */
public class OperatorInvitationNotPendingException extends OperatorInvitationException {

    public OperatorInvitationNotPendingException(String message) {
        super("OPERATOR_INVITATION_NOT_PENDING", 409, message);
    }
}
