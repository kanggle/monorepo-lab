package com.example.erp.notification.infrastructure.messaging;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Event-consumer observability counter (architecture.md § Observability):
 * {@code notification_event_dlt_total{topic}}. (The dedupe-skip + dispatch /
 * delivery counters live on the application-side metrics port.)
 */
@Component
public class ConsumerMetrics {

    private final MeterRegistry registry;

    public ConsumerMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void dlt(String topic) {
        registry.counter("notification_event_dlt_total", "topic", topic).increment();
    }
}
