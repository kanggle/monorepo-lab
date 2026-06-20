package com.example.settlement.application.port;

/**
 * Outbound port for settlement observability counters (architecture.md §
 * observability). Keeps the application layer free of Micrometer types; the
 * Micrometer adapter lives in infrastructure.
 */
public interface SettlementMetricsPort {

    /** {@code settlement_period_closed_total} — incremented once per successful close (AC-8). */
    void recordPeriodClosed();
}
