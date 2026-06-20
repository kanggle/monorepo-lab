package com.example.settlement.application.port;

/**
 * Outbound port for settlement observability counters (architecture.md §
 * observability). Keeps the application layer free of Micrometer types; the
 * Micrometer adapter lives in infrastructure.
 */
public interface SettlementMetricsPort {

    /** {@code settlement_period_closed_total} — incremented once per successful close (AC-8). */
    void recordPeriodClosed();

    /**
     * {@code settlement_payout_total{status}} — incremented once per payout execution
     * outcome (observability.md, TASK-BE-416 AC-6).
     *
     * @param status the execution outcome — {@code "PAID"} or {@code "FAILED"}
     */
    void recordPayoutExecuted(String status);
}
