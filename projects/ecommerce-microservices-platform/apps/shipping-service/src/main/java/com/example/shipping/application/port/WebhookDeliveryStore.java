package com.example.shipping.application.port;

/**
 * Idempotency store for carrier webhook deliveries (TASK-BE-294). Carriers retry
 * webhook deliveries, so each delivery carries a unique id and must take effect at most
 * once.
 *
 * <p>Implementations participate in the caller's transaction so that a failed webhook
 * processing rolls back the registration too (the delivery can then be retried). A
 * concurrent duplicate insert is treated as already-seen (best-effort, never throws).
 */
public interface WebhookDeliveryStore {

    /**
     * Records {@code deliveryId} as processed. Returns {@code true} when it was newly
     * recorded (first sight ⇒ proceed), {@code false} when it was already present or a
     * concurrent insert lost the race (⇒ duplicate, skip).
     */
    boolean registerIfFirst(String deliveryId);
}
