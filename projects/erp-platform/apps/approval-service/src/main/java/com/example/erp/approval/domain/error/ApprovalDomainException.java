package com.example.erp.approval.domain.error;

/**
 * Base for all approval-service domain exceptions. Each subclass carries a
 * stable erp error {@code code} (approval-api.md § Error code → HTTP). The
 * {@code presentation/advice/GlobalExceptionHandler} maps the code to its HTTP
 * status — the domain never references HTTP/Spring types (E: layer boundary).
 *
 * <p>Pure Java — no framework imports.
 */
public abstract class ApprovalDomainException extends RuntimeException {

    private final String code;

    protected ApprovalDomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
