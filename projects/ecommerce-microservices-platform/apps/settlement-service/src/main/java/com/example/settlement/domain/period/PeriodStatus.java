package com.example.settlement.domain.period;

/**
 * Settlement-period lifecycle state (architecture.md § Period close). An
 * {@code OPEN} period may be closed (aggregating its in-window accruals into
 * {@code seller_payout} rows); a {@code CLOSED} period is terminal — a second close
 * is rejected ({@code PERIOD_ALREADY_CLOSED}). There is no reopen (forward-declared,
 * mirrors finance {@code AccountingPeriod}).
 */
public enum PeriodStatus {
    OPEN,
    CLOSED
}
