package com.example.erp.readmodel.adapter.outbound.metrics;

import com.example.erp.readmodel.application.port.outbound.OrgViewMetricsPort;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Micrometer adapter for the read-API observability counter
 * {@code read_model_org_view_unresolved_total{reference}} (architecture.md
 * § Observability) — counts org-views served with an unresolved reference
 * (source-not-yet-consumed signal).
 */
@Component
public class MicrometerOrgViewMetricsAdapter implements OrgViewMetricsPort {

    private static final String METRIC = "read_model_org_view_unresolved_total";

    private final MeterRegistry registry;

    public MicrometerOrgViewMetricsAdapter(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void recordUnresolved(String reference) {
        registry.counter(METRIC, "reference", reference).increment();
    }
}
