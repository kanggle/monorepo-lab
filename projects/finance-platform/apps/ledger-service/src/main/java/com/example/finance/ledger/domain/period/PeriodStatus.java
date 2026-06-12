package com.example.finance.ledger.domain.period;

/**
 * Accounting period lifecycle state (architecture.md § Accounting Period). An
 * {@code OPEN} period accepts postings whose {@code postedAt} falls in its window;
 * a {@code CLOSED} period locks the books for that window (the posting guard
 * rejects any entry it covers — {@code LEDGER_PERIOD_CLOSED}). There is no reopen
 * (forward-declared).
 */
public enum PeriodStatus {
    OPEN,
    CLOSED
}
