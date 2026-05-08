package com.wms.admin.infra.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * Cross-cutting Micrometer wiring for the projection consumers.
 * Per {@code architecture.md § Observability}:
 *
 * <ul>
 *   <li>{@code admin.projection.lag.seconds{source_service,topic}} — event time
 *       to applied time</li>
 *   <li>{@code admin.projection.dropped.count{reason=stale|duplicate}} —
 *       late-event drops + dedupe hits</li>
 *   <li>{@code admin.projection.error.count{topic}} — exception rate per
 *       consumer</li>
 *   <li>{@code admin.query.latency.p95{endpoint}} — slow dashboards visible</li>
 *   <li>{@code admin.query.cache.hit.rate} — Redis cache effectiveness (v1
 *       always 0; future v2)</li>
 * </ul>
 */
@Component
public class ProjectionMetrics {

    public static final String LAG_METRIC = "admin.projection.lag.seconds";
    public static final String DROPPED_METRIC = "admin.projection.dropped.count";
    public static final String ERROR_METRIC = "admin.projection.error.count";
    public static final String QUERY_LATENCY_METRIC = "admin.query.latency.p95";
    public static final String CACHE_HIT_RATE_METRIC = "admin.query.cache.hit.rate";

    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final ConcurrentMap<String, Timer> lagTimers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> droppedCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> errorCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Timer> queryTimers = new ConcurrentHashMap<>();

    public ProjectionMetrics(MeterRegistry meterRegistry, Clock clock) {
        this.meterRegistry = meterRegistry;
        this.clock = clock;
        // v1: cache miss rate is always 0 — register the gauge so the metric
        // exists in the /actuator/prometheus dump for downstream alerting.
        meterRegistry.gauge(CACHE_HIT_RATE_METRIC, 0.0d);
    }

    public void recordLag(String sourceService, String topic, Instant eventTime) {
        if (eventTime == null) return;
        Duration delta = Duration.between(eventTime, clock.instant());
        Timer timer = lagTimers.computeIfAbsent(
                sourceService + "|" + topic,
                key -> Timer.builder(LAG_METRIC)
                        .tags(Tags.of("source_service", sourceService, "topic", topic))
                        .register(meterRegistry));
        timer.record(delta);
    }

    public void recordDropped(String reason) {
        droppedCounters.computeIfAbsent(reason,
                r -> Counter.builder(DROPPED_METRIC)
                        .tags(Tags.of("reason", r))
                        .register(meterRegistry))
                .increment();
    }

    public void recordError(String topic) {
        errorCounters.computeIfAbsent(topic,
                t -> Counter.builder(ERROR_METRIC)
                        .tags(Tags.of("topic", t))
                        .register(meterRegistry))
                .increment();
    }

    /** Returns a {@link Timer.Sample} the controller can stop after rendering its response. */
    public Timer.Sample startQueryTimer() {
        return Timer.start(meterRegistry);
    }

    public void stopQueryTimer(Timer.Sample sample, String endpoint) {
        Timer timer = queryTimers.computeIfAbsent(endpoint,
                e -> Timer.builder(QUERY_LATENCY_METRIC)
                        .tags(Tags.of("endpoint", e))
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .register(meterRegistry));
        sample.stop(timer);
    }
}
