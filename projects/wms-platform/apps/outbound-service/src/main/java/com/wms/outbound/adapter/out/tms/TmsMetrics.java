package com.wms.outbound.adapter.out.tms;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Centralised Micrometer wiring for the TMS adapter
 * (per {@code external-integrations.md} §2.12 and architecture.md
 * §Observability).
 *
 * <p>Exposes:
 * <ul>
 *   <li>{@code outbound.tms.request.count{result}} — counter, see
 *       {@link Result} for the tag values</li>
 *   <li>{@code outbound.tms.request.duration.seconds} — Timer
 *       (Micrometer derives p50/p95/p99 from the histogram)</li>
 *   <li>{@code outbound.tms.retry.count{attempt}} — counter</li>
 *   <li>{@code outbound.tms.circuit.state{vendor=tms}} — gauge
 *       (0 closed, 1 half-open, 2 open). Backed by Resilience4j's
 *       {@link CircuitBreaker} state.</li>
 *   <li>{@code outbound.tms.dedupe.cache_hit.count} — counter</li>
 * </ul>
 */
@Component
@Profile("!standalone")
public class TmsMetrics {

    static final String CIRCUIT_NAME = "tms-client";

    private final MeterRegistry meterRegistry;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    public TmsMetrics(MeterRegistry meterRegistry,
                      CircuitBreakerRegistry circuitBreakerRegistry) {
        this.meterRegistry = meterRegistry;
        this.circuitBreakerRegistry = circuitBreakerRegistry;

        meterRegistry.gauge("outbound.tms.circuit.state",
                java.util.List.of(Tag.of("vendor", "tms")),
                this,
                TmsMetrics::circuitStateValue);
    }

    /** Result tag values for {@code outbound.tms.request.count}. */
    enum Result {
        success, timeout, server_5xx, client_4xx, circuit_open, dedupe_hit
    }

    void recordResult(Result result) {
        meterRegistry.counter("outbound.tms.request.count",
                "result", result.name()).increment();
    }

    Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    void stopTimer(Timer.Sample sample) {
        sample.stop(Timer.builder("outbound.tms.request.duration.seconds")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry));
    }

    void recordRetryAttempt(int attempt) {
        meterRegistry.counter("outbound.tms.retry.count",
                "attempt", String.valueOf(attempt)).increment();
    }

    void recordDedupeHit() {
        meterRegistry.counter("outbound.tms.dedupe.cache_hit.count").increment();
    }

    /**
     * Gauge value for {@code outbound.tms.circuit.state}: 0 closed, 1 half-open, 2 open.
     * Returns 0 if the circuit hasn't been registered yet (boot lag).
     */
    private double circuitStateValue() {
        try {
            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(CIRCUIT_NAME);
            return switch (cb.getState()) {
                case CLOSED, DISABLED, METRICS_ONLY -> 0.0;
                case HALF_OPEN -> 1.0;
                case OPEN, FORCED_OPEN -> 2.0;
            };
        } catch (RuntimeException e) {
            return 0.0;
        }
    }

    static long toNanos(long millis) {
        return TimeUnit.MILLISECONDS.toNanos(millis);
    }
}
