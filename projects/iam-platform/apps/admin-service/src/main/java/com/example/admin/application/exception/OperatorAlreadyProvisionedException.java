package com.example.admin.application.exception;

/**
 * 409 {@code OPERATOR_ALREADY_PROVISIONED} — born in TASK-MONO-772 S3 (owner decision OD-1 — «one person = one
 * company»: any operator facet, any status), NARROWED by TASK-MONO-784 S5 (ADR-MONO-082 b · D2 · D-12): the
 * account's operator facet ({@code admin_operators.oidc_subject = accountId}) cannot take THIS membership — it is a
 * platform ({@code '*'}) row, it is not {@code ACTIVE}, or it is already a member of the tenant. An ACTIVE company
 * facet that is not yet a member receives the membership instead ({@code OperatorMembershipAddition}). Raised by
 * the invitation acceptance (step 6 ③) and, before any tenant is created, by self-service onboarding. The code
 * string is unchanged so the IdP and console mappings stay as they are.
 */
public class OperatorAlreadyProvisionedException extends OperatorInvitationException {

    public OperatorAlreadyProvisionedException(String message) {
        super("OPERATOR_ALREADY_PROVISIONED", 409, message);
    }
}
