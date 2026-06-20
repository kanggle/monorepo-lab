package com.example.settlement.infrastructure.metrics;

import com.example.settlement.application.port.SettlementMetricsPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Micrometer adapter for {@link SettlementMetricsPort}. Registers
 * {@code settlement_period_closed_total} (AC-8) and
 * {@code settlement_payout_total{status}} (TASK-BE-416 AC-6).
 */
@Component
public class MicrometerSettlementMetrics implements SettlementMetricsPort {

    private static final String PAYOUT_METRIC = "settlement_payout_total";
    private static final String STATUS_TAG = "status";

    private final Counter periodClosedTotal;
    private final MeterRegistry registry;

    public MicrometerSettlementMetrics(MeterRegistry registry) {
        Objects.requireNonNull(registry, "MeterRegistry must not be null");
        this.registry = registry;
        this.periodClosedTotal = Counter.builder("settlement_period_closed_total")
                .description("Total settlement periods closed")
                .register(registry);
    }

    @Override
    public void recordPeriodClosed() {
        periodClosedTotal.increment();
    }

    /**
     * Increments {@code settlement_payout_total{status=PAID|FAILED}} (observability.md).
     * One counter per payout resolved; tagged by status so both dimensions are
     * independently queryable.
     */
    @Override
    public void recordPayoutExecuted(String status) {
        Counter.builder(PAYOUT_METRIC)
                .description("Total seller payouts executed by the simulated (or future real) payout adapter")
                .tag(STATUS_TAG, status)
                .register(registry)
                .increment();
    }
}
