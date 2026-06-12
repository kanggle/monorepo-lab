package com.example.finance.ledger.domain.error;

/**
 * Base for all ledger-service domain exceptions. Each subclass carries a stable
 * fintech error {@code code} (ledger-api.md § Error codes). The
 * {@code presentation/advice/GlobalExceptionHandler} maps the code to its HTTP
 * status — the domain never references HTTP/Spring types (layer boundary).
 *
 * <p>Pure Java — no framework imports.
 */
public abstract class LedgerDomainException extends RuntimeException {

    private final String code;

    protected LedgerDomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
