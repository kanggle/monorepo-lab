package com.example.admin.application.exception;

/**
 * TASK-MONO-772 S3 — 403 {@code OPERATOR_INVITATION_ACCOUNT_NOT_ELIGIBLE}: no such account, or not an ACTIVE consumer-pool account
 * (a site / {@code iam} / B2B account) — account-service's one 404 (S1-3, step 4).
 */
public class OperatorInvitationAccountNotEligibleException extends OperatorInvitationException {

    public OperatorInvitationAccountNotEligibleException(String message) {
        super("OPERATOR_INVITATION_ACCOUNT_NOT_ELIGIBLE", 403, message);
    }
}
