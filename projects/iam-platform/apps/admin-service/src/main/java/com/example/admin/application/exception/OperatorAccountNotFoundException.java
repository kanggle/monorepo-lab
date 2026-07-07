package com.example.admin.application.exception;

/**
 * Raised when {@code POST /api/admin/operators} targets an email that has NO
 * signed-up account in the target tenant.
 *
 * <p>TASK-MONO-334 (ADR-MONO-035 amendment): an operator may only be created
 * for an email that already owns a tenant account — that account's unified IAM
 * (OIDC) credential is the operator's primary login. This supersedes the
 * TASK-PC-FE-179 fail-soft advisory: a "dangling" account-less operator is no
 * longer creatable, even with a break-glass password. Surfaces as
 * {@code 422 OPERATOR_ACCOUNT_NOT_FOUND}.
 *
 * <p>Platform-scope ({@code "*"}) creates are exempt — there is no account_db
 * tenant row to verify against (the SUPER_ADMIN bootstrap path).
 */
public class OperatorAccountNotFoundException extends RuntimeException {
    public OperatorAccountNotFoundException(String message) {
        super(message);
    }
}
