package com.example.erp.readmodel.adapter.inbound.messaging;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Event-consumer observability counters (architecture.md § Observability):
 * {@code read_model_event_dedupe_skipped_total},
 * {@code read_model_event_dlt_total{topic}},
 * {@code read_model_projection_applied_total{aggregate,changeKind}}.
 */
@Component
public class ConsumerMetrics {

    private final MeterRegistry registry;

    public ConsumerMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void dlt(String topic) {
        registry.counter("read_model_event_dlt_total", "topic", topic).increment();
    }

    public void applied(String aggregate, String changeKind) {
        registry.counter("read_model_projection_applied_total",
                "aggregate", aggregate, "changeKind", changeKind).increment();
    }
}
