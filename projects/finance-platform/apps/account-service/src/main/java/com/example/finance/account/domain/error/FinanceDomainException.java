package com.example.finance.account.domain.error;

/**
 * Base for all account-service domain exceptions. Each subclass carries a
 * stable fintech error {@code code} (account-api.md § Error code → HTTP). The
 * {@code presentation/advice/GlobalExceptionHandler} maps the code to its HTTP
 * status — the domain never references HTTP/Spring types (F: layer boundary).
 *
 * <p>Pure Java — no framework imports.
 */
public abstract class FinanceDomainException extends RuntimeException {

    private final String code;

    protected FinanceDomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
