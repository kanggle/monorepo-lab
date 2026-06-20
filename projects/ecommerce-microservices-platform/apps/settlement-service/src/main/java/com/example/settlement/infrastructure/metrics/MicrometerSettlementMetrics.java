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
 *
 * <p>W-3: all counters are pre-registered at construction (idiomatic Micrometer —
 * mirrors the {@code periodClosedTotal} pattern). Pre-registration ensures the counters
 * appear in {@code /actuator/metrics} from startup (value=0) and avoids
 * building+registering on every hot-path call.
 */
@Component
public class MicrometerSettlementMetrics implements SettlementMetricsPort {

    private static final String PAYOUT_METRIC = "settlement_payout_total";
    private static final String PAYOUT_DESCRIPTION =
            "Total seller payouts executed by the simulated (or future real) payout adapter";
    private static final String STATUS_TAG = "status";

    private final Counter periodClosedTotal;
    private final Counter payoutPaidTotal;
    private final Counter payoutFailedTotal;

    public MicrometerSettlementMetrics(MeterRegistry registry) {
        Objects.requireNonNull(registry, "MeterRegistry must not be null");
        this.periodClosedTotal = Counter.builder("settlement_period_closed_total")
                .description("Total settlement periods closed")
                .register(registry);
        this.payoutPaidTotal = Counter.builder(PAYOUT_METRIC)
                .description(PAYOUT_DESCRIPTION)
                .tag(STATUS_TAG, "PAID")
                .register(registry);
        this.payoutFailedTotal = Counter.builder(PAYOUT_METRIC)
                .description(PAYOUT_DESCRIPTION)
                .tag(STATUS_TAG, "FAILED")
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
        if ("PAID".equals(status)) {
            payoutPaidTotal.increment();
        } else {
            payoutFailedTotal.increment();
        }
    }
}
