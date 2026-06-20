package com.example.settlement.domain.period;

/**
 * Thrown when a period is not found in the caller's tenant (absent or cross-tenant).
 * Surfaces as HTTP 404 {@code SETTLEMENT_NOT_FOUND} (404-over-403, no cross-tenant
 * existence disclosure — M3). A domain exception (no framework types).
 */
public class PeriodNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PeriodNotFoundException(String message) {
        super(message);
    }
}
