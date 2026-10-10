package com.example.admin.application.exception;

/**
 * TASK-MONO-772 S3 — 409 {@code OPERATOR_INVITATION_INVALIDATED}: the basis the invitation was issued on no longer holds — the tenant is
 * not ACTIVE, the inviter is not ACTIVE, or the inviter's admin-grant scope (ADR-MONO-024 D2) / grant menu (D3) no
 * longer covers it (S1-4, step 5). The inviting side has to send it again.
 */
public class OperatorInvitationInvalidatedException extends OperatorInvitationException {

    public OperatorInvitationInvalidatedException(String message) {
        super("OPERATOR_INVITATION_INVALIDATED", 409, message);
    }
}
