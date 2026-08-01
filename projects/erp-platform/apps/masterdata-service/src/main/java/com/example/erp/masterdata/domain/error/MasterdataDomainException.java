package com.example.erp.masterdata.domain.error;

import java.util.Map;

/**
 * Base for all masterdata-service domain exceptions. Each subclass carries a
 * stable erp error {@code code} (masterdata-api.md § Error code → HTTP). The
 * {@code presentation/advice/GlobalExceptionHandler} maps the code to its HTTP
 * status — the domain never references HTTP/Spring types (E: layer boundary).
 *
 * <p>A subclass may additionally carry {@link #details()} — the structured context
 * {@code masterdata-api.md} documents for specific codes (today only
 * {@code MASTERDATA_REFERENCE_VIOLATION}: "{@code details} enumerates the referencer
 * kinds"). It is {@code null} for every other code and is then omitted from the wire
 * body by {@code @JsonInclude(NON_NULL)}. Plain {@code Map} — no framework type, so
 * the domain boundary holds.
 *
 * <p>Pure Java — no framework imports.
 */
public abstract class MasterdataDomainException extends RuntimeException {

    private final String code;
    private final Map<String, Object> details;

    protected MasterdataDomainException(String code, String message) {
        this(code, message, null);
    }

    protected MasterdataDomainException(String code, String message,
                                        Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = (details == null || details.isEmpty()) ? null : Map.copyOf(details);
    }

    public String code() {
        return code;
    }

    /** Structured error context, or {@code null} when this code carries none. */
    public Map<String, Object> details() {
        return details;
    }
}
