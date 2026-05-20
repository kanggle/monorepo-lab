package com.kanggle.platformconsole.bff.infrastructure.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Eager registration of the 3 mandatory BFF metric families (AC-10).
 *
 * <p>Per architecture.md § Observability (D7.A) and
 * {@code console-integration-contract.md} § 2.4.9, the Prometheus-exposed
 * metric names are:
 * <ul>
 *   <li>{@code bff_fanout_latency_seconds} — histogram per outbound leg</li>
 *   <li>{@code bff_fanout_errors_total} — counter per outbound leg failure</li>
 *   <li>{@code bff_aggregation_degrade_count_total} — counter on degrade rendering</li>
 * </ul>
 *
 * <p><b>Micrometer naming convention</b> — Counters register without the
 * {@code _total} suffix (Prometheus exposition adds it automatically). Timers
 * register without the {@code _seconds} suffix (Timer base unit is seconds —
 * the Prometheus naming convention appends {@code _seconds} to the exposition
 * name automatically). Registering the suffix in the base name triggers a
 * double-suffix in some {@code PrometheusMeterRegistry} versions (e.g.
 * {@code bff_fanout_errors_total_total}) — surfaced as a CI Linux regression
 * on this skeleton's first push.
 *
 * <p>Registered eagerly with at least one observation (Counter
 * {@code .increment(0.0)} / Timer {@code .record(Duration.ZERO)}) so the
 * series appears in {@code /actuator/prometheus} even before any composition
 * route has fired. TASK-PC-FE-011 uses the same metric names with additional
 * labels when it adds the first real route.
 *
 * <p>OTel {@code traceparent} propagation on outbound {@code RestClient} is
 * handled by Spring Boot 3.4 auto-configuration via
 * {@code micrometer-tracing-bridge-otel} on the classpath — outbound HTTP calls
 * automatically carry W3C trace context when {@code ObservationRegistry} is active.
 */
@Configuration
public class ObservabilityConfig {

    /**
     * Eager Timer (histogram) for per-outbound-domain fan-out latency.
     * Base name: {@code bff_fanout_latency}; Prometheus exposition appends
     * {@code _seconds} (Timer base unit). Labels: {@code domain}, {@code route}
     * (added per call by TASK-PC-FE-011).
     */
    @Bean
    public Timer bffFanoutLatencyTimer(MeterRegistry registry) {
        Timer timer = Timer.builder("bff_fanout_latency")
                .description("Latency histogram for BFF outbound domain fan-out legs")
                .tag("domain", "none")
                .tag("route", "none")
                .register(registry);
        timer.record(java.time.Duration.ZERO);
        return timer;
    }

    /**
     * Eager Counter for per-outbound-domain fan-out errors.
     * Base name: {@code bff_fanout_errors}; Prometheus exposition appends
     * {@code _total} (Counter convention). Labels: {@code domain}, {@code route},
     * {@code code} (added per call by TASK-PC-FE-011).
     */
    @Bean
    public Counter bffFanoutErrorsCounter(MeterRegistry registry) {
        Counter counter = Counter.builder("bff_fanout_errors")
                .description("Counter for BFF outbound domain fan-out leg failures")
                .tag("domain", "none")
                .tag("route", "none")
                .tag("code", "none")
                .register(registry);
        counter.increment(0.0);
        return counter;
    }

    /**
     * Eager Counter for composition degrade events.
     * Base name: {@code bff_aggregation_degrade_count}; Prometheus exposition
     * appends {@code _total} (Counter convention). Labels: {@code dashboard},
     * {@code degraded_domain} (added per call by TASK-PC-FE-011).
     */
    @Bean
    public Counter bffAggregationDegradeCounter(MeterRegistry registry) {
        Counter counter = Counter.builder("bff_aggregation_degrade_count")
                .description("Counter for BFF composition degrade events")
                .tag("dashboard", "none")
                .tag("degraded_domain", "none")
                .register(registry);
        counter.increment(0.0);
        return counter;
    }
}
