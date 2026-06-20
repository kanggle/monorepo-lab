package com.example.settlement.domain.period;

/**
 * Thrown when a settlement period is opened over an empty / inverted window
 * ({@code from ≥ to}). Surfaces as HTTP 422 {@code PERIOD_WINDOW_INVALID} on
 * {@code POST /periods} (AC-7). A domain exception (no framework types) so the
 * half-open non-empty-window invariant is enforced inside {@link SettlementPeriod}
 * regardless of caller.
 */
public class PeriodWindowInvalidException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PeriodWindowInvalidException(String message) {
        super(message);
    }
}
