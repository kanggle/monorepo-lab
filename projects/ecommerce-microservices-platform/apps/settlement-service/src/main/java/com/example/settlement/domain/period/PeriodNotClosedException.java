package com.example.settlement.domain.period;

/**
 * Thrown when a payout-execute operation is attempted on a period that is not yet
 * CLOSED (settlement-api.md {@code PERIOD_NOT_CLOSED} 409). A period must be
 * CLOSED before its payout rows can be executed (two-step payout, architecture.md
 * § Period close + simulated payout — TASK-BE-416).
 */
public class PeriodNotClosedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PeriodNotClosedException(String message) {
        super(message);
    }
}
