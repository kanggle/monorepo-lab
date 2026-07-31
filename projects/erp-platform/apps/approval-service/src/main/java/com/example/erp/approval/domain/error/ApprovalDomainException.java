package com.example.erp.approval.domain.error;

import java.util.Map;

/**
 * Base for all approval-service domain exceptions. Each subclass carries a
 * stable erp error {@code code} (approval-api.md § Error code → HTTP). The
 * {@code presentation/advice/GlobalExceptionHandler} maps the code to its HTTP
 * status — the domain never references HTTP/Spring types (E: layer boundary).
 *
 * <p>A subclass may additionally carry {@link #details()} — the structured context
 * {@code approval-api.md} documents for specific codes ({@code APPROVAL_ROUTE_INVALID}'s
 * {@code details.cause}, {@code APPROVAL_NOT_AUTHORIZED_APPROVER}'s {@code details.role}).
 * It is {@code null} for every other code and is then omitted from the wire body by
 * {@code @JsonInclude(NON_NULL)}. Plain {@code Map} — no framework type, so the domain
 * boundary holds.
 *
 * <p>Pure Java — no framework imports.
 */
public abstract class ApprovalDomainException extends RuntimeException {

    private final String code;
    private final Map<String, Object> details;

    protected ApprovalDomainException(String code, String message) {
        this(code, message, null);
    }

    protected ApprovalDomainException(String code, String message,
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
