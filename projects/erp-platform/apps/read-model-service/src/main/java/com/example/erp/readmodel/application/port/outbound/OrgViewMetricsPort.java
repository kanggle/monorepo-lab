package com.example.erp.readmodel.application.port.outbound;

/**
 * Outbound port for read-API observability counters (keeps Micrometer types out
 * of the application layer). The Micrometer adapter implements
 * {@code read_model_org_view_unresolved_total{reference}} — a source-not-yet-
 * consumed signal (architecture.md § Observability).
 */
public interface OrgViewMetricsPort {

    /** Increments the unresolved-reference counter for the given reference name. */
    void recordUnresolved(String reference);
}
