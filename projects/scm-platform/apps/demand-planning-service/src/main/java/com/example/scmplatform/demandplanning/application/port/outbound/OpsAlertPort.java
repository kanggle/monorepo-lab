package com.example.scmplatform.demandplanning.application.port.outbound;

/**
 * Outbound port for ops alerting on non-retryable consumer failures.
 * Best-effort: publish failure is logged but does not affect the main flow.
 */
public interface OpsAlertPort {

    /**
     * Send an ops alert for an unmapped SKU (non-retryable DLT path).
     * Best-effort: any exception is swallowed and logged.
     */
    void alertUnmappedSku(String skuCode, String eventId, String sourceTopic);
}
