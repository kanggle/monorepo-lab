package com.example.admin.application.exception;

/**
 * TASK-MONO-772 S3 — 409 {@code OPERATOR_INVITATION_ALREADY_USED}: another account already accepted this invitation (auth-to-admin.md
 * § accept, step 2 — the same account resubmitting is a 200, not this).
 */
public class OperatorInvitationAlreadyUsedException extends OperatorInvitationException {

    public OperatorInvitationAlreadyUsedException(String message) {
        super("OPERATOR_INVITATION_ALREADY_USED", 409, message);
    }
}
