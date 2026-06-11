package com.example.scmplatform.demandplanning.domain.error;

/**
 * Raised when the synchronous procurement DRAFT-PO call (ADR-MONO-027 D5) fails
 * — connection refused, timeout, or a non-2xx response. Mapped to 503 so the
 * operator retries; the suggestion stays {@code APPROVED} (materialization is
 * the second, separate transaction), and the retry is idempotent on
 * {@code sourceSuggestionId} so no orphan / duplicate PO results.
 */
public class ProcurementUnavailableException extends RuntimeException {

    public ProcurementUnavailableException(String message) {
        super(message);
    }

    public ProcurementUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
