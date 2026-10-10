package com.example.admin.application.exception;

/**
 * TASK-MONO-772 — 404 {@code OPERATOR_INVITATION_NOT_FOUND}: no such invitation, OR one in a tenant outside the
 * actor's admin-grant scope. The two are deliberately one answer (enumeration-safe — the same shape as
 * {@code GROUP_NOT_FOUND}).
 */
public class OperatorInvitationNotFoundException extends OperatorInvitationException {

    public OperatorInvitationNotFoundException(String message) {
        super("OPERATOR_INVITATION_NOT_FOUND", 404, message);
    }
}
