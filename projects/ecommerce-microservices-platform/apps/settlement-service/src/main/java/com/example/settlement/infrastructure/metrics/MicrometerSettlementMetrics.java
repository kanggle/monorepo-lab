package com.example.settlement.infrastructure.metrics;

import com.example.settlement.application.port.SettlementMetricsPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Micrometer adapter for {@link SettlementMetricsPort}. Registers
 * {@code settlement_period_closed_total} (AC-8) — incremented once per successful
 * period close.
 */
@Component
public class MicrometerSettlementMetrics implements SettlementMetricsPort {

    private final Counter periodClosedTotal;

    public MicrometerSettlementMetrics(MeterRegistry registry) {
        Objects.requireNonNull(registry, "MeterRegistry must not be null");
        this.periodClosedTotal = Counter.builder("settlement_period_closed_total")
                .description("Total settlement periods closed")
                .register(registry);
    }

    @Override
    public void recordPeriodClosed() {
        periodClosedTotal.increment();
    }
}
