package com.example.admin.application.exception;

/**
 * TASK-MONO-772 — 409 {@code OPERATOR_INVITATION_ALREADY_PENDING}: the tenant already has a {@code PENDING}
 * invitation (expired included) for this email. Raised by the application pre-check AND by the
 * {@code uk_operator_invitation_pending_key} unique key when two creates race — same answer either way.
 * The console offers that row's resend.
 */
public class OperatorInvitationAlreadyPendingException extends OperatorInvitationException {

    public OperatorInvitationAlreadyPendingException(String message) {
        super("OPERATOR_INVITATION_ALREADY_PENDING", 409, message);
    }
}
