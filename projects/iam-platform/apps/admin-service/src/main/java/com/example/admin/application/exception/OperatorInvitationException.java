package com.example.admin.application.exception;

/**
 * TASK-MONO-772 — the base of the operator-invitation refusals (admin-api.md § Operator Invitation ·
 * auth-to-admin.md § accept). Each subclass fixes its public {@code code} and HTTP status in its own super
 * constructor call with a string literal, so {@code scripts/check-error-code-registry.sh} sees every code, and
 * {@code AdminExceptionHandler} needs one handler for the whole family (S3 adds the acceptance refusals here
 * without touching the handler).
 *
 * <p>The status is a plain {@code int}: this is the application layer and must not import Spring's
 * {@code HttpStatus}.
 */
public abstract class OperatorInvitationException extends RuntimeException {

    private final String code;
    private final int httpStatus;

    protected OperatorInvitationException(String code, int httpStatus, String message) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
