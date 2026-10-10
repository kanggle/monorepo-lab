package com.example.admin.application.exception;

/**
 * TASK-MONO-772 S3 — 409 {@code OPERATOR_ALREADY_PROVISIONED} (owner decision OD-1 — one person = one company in 772): the account
 * already has an operator facet ({@code admin_operators.oidc_subject = accountId}, any status). Raised by the
 * invitation acceptance (step 6) and, before any tenant is created, by self-service onboarding (S1-11).
 */
public class OperatorAlreadyProvisionedException extends OperatorInvitationException {

    public OperatorAlreadyProvisionedException(String message) {
        super("OPERATOR_ALREADY_PROVISIONED", 409, message);
    }
}
