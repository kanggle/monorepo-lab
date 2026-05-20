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
 * {@code console-integration-contract.md} § 2.4.9:
 * <ul>
 *   <li>{@code bff_fanout_latency_seconds} — histogram per outbound leg</li>
 *   <li>{@code bff_fanout_errors_total} — counter per outbound leg failure</li>
 *   <li>{@code bff_aggregation_degrade_count_total} — counter on degrade rendering</li>
 * </ul>
 *
 * <p>Registered eagerly (0-sample) so they appear in {@code /actuator/prometheus}
 * even before any composition route has fired. TASK-PC-FE-011 uses the same
 * metric names with additional labels when it adds the first real route.
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
     * Labels: {@code domain}, {@code route} (added per call by TASK-PC-FE-011).
     */
    @Bean
    public Timer bffFanoutLatencyTimer(MeterRegistry registry) {
        return Timer.builder("bff_fanout_latency_seconds")
                .description("Latency histogram for BFF outbound domain fan-out legs")
                .tag("domain", "none")
                .tag("route", "none")
                .register(registry);
    }

    /**
     * Eager Counter for per-outbound-domain fan-out errors.
     * Labels: {@code domain}, {@code route}, {@code code} (added per call by TASK-PC-FE-011).
     */
    @Bean
    public Counter bffFanoutErrorsCounter(MeterRegistry registry) {
        return Counter.builder("bff_fanout_errors_total")
                .description("Counter for BFF outbound domain fan-out leg failures")
                .tag("domain", "none")
                .tag("route", "none")
                .tag("code", "none")
                .register(registry);
    }

    /**
     * Eager Counter for composition degrade events.
     * Labels: {@code dashboard}, {@code degraded_domain} (added per call by TASK-PC-FE-011).
     */
    @Bean
    public Counter bffAggregationDegradeCounter(MeterRegistry registry) {
        return Counter.builder("bff_aggregation_degrade_count_total")
                .description("Counter for BFF composition degrade events")
                .tag("dashboard", "none")
                .tag("degraded_domain", "none")
                .register(registry);
    }
}
