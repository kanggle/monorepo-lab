package com.example.admin.application.exception;

/**
 * TASK-BE-631 (ADR-MONO-083) — the base of the domain-permission-set refusals (admin-api.md § 도메인 업무 권한 세트
 * error tables; platform/error-handling.md registers the six codes). Same shape as {@link OperatorInvitationException}:
 * each subclass fixes its public {@code code} and HTTP status in its own {@code super(code, status, …)} call, so
 * {@code scripts/check-error-code-registry.sh} sees every code and {@code AdminExceptionHandler} needs one handler.
 *
 * <p>The status is a plain {@code int}: this is the application layer and must not import Spring's
 * {@code HttpStatus}. Every refusal of this family writes nothing.
 */
public abstract class DomainPermissionSetException extends RuntimeException {

    private final String code;
    private final int httpStatus;

    protected DomainPermissionSetException(String code, int httpStatus, String message) {
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
