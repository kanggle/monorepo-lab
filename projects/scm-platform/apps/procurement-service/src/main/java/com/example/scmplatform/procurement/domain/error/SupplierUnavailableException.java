package com.example.scmplatform.procurement.domain.error;

/**
 * Mapped to HTTP 503 {@code SUPPLIER_UNAVAILABLE}. Raised when the supplier
 * adapter circuit breaker is OPEN, retries are exhausted, or the supplier
 * returned a 5xx beyond the retry budget.
 */
public class SupplierUnavailableException extends RuntimeException {
    public SupplierUnavailableException(String message) {
        super(message);
    }

    public SupplierUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
