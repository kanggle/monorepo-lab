package com.wms.notification.application.port.in;

/**
 * Inbound port for the retry scheduler — picks PENDING deliveries whose
 * {@code scheduledRetryAt} has elapsed and re-invokes
 * {@code DeliveryExecutor}. v2 will additionally expose a manual retry
 * REST endpoint that funnels through this same port.
 */
public interface RetryFailedDeliveryUseCase {

    /**
     * Retry one delivery by id. Used by the scheduler and (v2) by the admin
     * surface. No-ops when the delivery is already terminal.
     *
     * @param deliveryId persistent id
     */
    void retry(java.util.UUID deliveryId);

    /**
     * Scan for due retries and dispatch each. Returns the count actually
     * dispatched (rows that the scheduler successfully picked under
     * SKIP LOCKED). Used by the scheduled retry job.
     */
    int dispatchDueRetries();
}
