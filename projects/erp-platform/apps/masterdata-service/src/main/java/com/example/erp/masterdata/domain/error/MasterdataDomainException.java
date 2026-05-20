package com.example.erp.masterdata.domain.error;

/**
 * Base for all masterdata-service domain exceptions. Each subclass carries a
 * stable erp error {@code code} (masterdata-api.md § Error code → HTTP). The
 * {@code presentation/advice/GlobalExceptionHandler} maps the code to its HTTP
 * status — the domain never references HTTP/Spring types (E: layer boundary).
 *
 * <p>Pure Java — no framework imports.
 */
public abstract class MasterdataDomainException extends RuntimeException {

    private final String code;

    protected MasterdataDomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
