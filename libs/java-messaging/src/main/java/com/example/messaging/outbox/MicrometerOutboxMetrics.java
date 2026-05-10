package com.example.messaging.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;

/**
 * Micrometer-backed implementation of {@link OutboxMetrics}.
 *
 * <p>Each service constructs one with its own metric prefix
 * (e.g. {@code "<service>"} → {@code <service>.outbox.publish.success.total}).
 * The pending-count gauge is registered separately because it requires a
 * reference to the repository — see {@link AbstractOutboxPublisher} constructor.
 *
 * <p>Resulting metric names per the canonical naming convention:
 * <ul>
 *   <li>{@code <prefix>.outbox.publish.success.total} (counter, tag: {@code event_type})</li>
 *   <li>{@code <prefix>.outbox.publish.failure.total} (counter, tags: {@code event_type}, {@code reason})</li>
 *   <li>{@code <prefix>.outbox.lag.seconds} (timer)</li>
 * </ul>
 */
public final class MicrometerOutboxMetrics implements OutboxMetrics {

    private final MeterRegistry registry;
    private final String metricPrefix;
    private final Timer lagTimer;

    public MicrometerOutboxMetrics(MeterRegistry registry, String metricPrefix) {
        this.registry = registry;
        this.metricPrefix = metricPrefix;
        this.lagTimer = Timer.builder(metricPrefix + ".outbox.lag.seconds")
                .description("Wall time between outbox row occurredAt and Kafka ACK")
                .register(registry);
    }

    @Override
    public void recordPublishSuccess(String eventType, Duration lag) {
        Counter.builder(metricPrefix + ".outbox.publish.success.total")
                .description("Outbox rows successfully forwarded to Kafka")
                .tags(Tags.of("event_type", nullSafe(eventType)))
                .register(registry)
                .increment();
        if (lag != null) {
            lagTimer.record(lag);
        }
    }

    @Override
    public void recordPublishFailure(String eventType, String reason) {
        Counter.builder(metricPrefix + ".outbox.publish.failure.total")
                .description("Outbox rows whose Kafka publish call failed")
                .tags(Tags.of("event_type", nullSafe(eventType), "reason", nullSafe(reason)))
                .register(registry)
                .increment();
    }

    private static String nullSafe(String value) {
        return value == null ? "unknown" : value;
    }
}
