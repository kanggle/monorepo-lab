package com.example.platform.notification.delivery;

/**
 * The result of dispatching one {@link DeliveryRecord} through
 * {@link DeliveryDispatcher#dispatch(DeliveryRecord)}. Returned to the service so
 * it can emit its own outbox event (the wms reference re-emits
 * {@code wms.notification.delivered.v1}) — the lib stays out of the outbox
 * (HARDSTOP-03: no domain event types, no service names).
 *
 * <ul>
 *   <li>{@link #SUCCEEDED}              — vendor accepted; record is terminal SUCCEEDED.</li>
 *   <li>{@link #FAILED_PERMANENT}       — do-not-retry vendor failure; record is terminal FAILED.</li>
 *   <li>{@link #RETRY_SCHEDULED}        — transient failure; record stays PENDING with a future
 *       {@code scheduledRetryAt}.</li>
 *   <li>{@link #FAILED_RETRY_EXHAUSTED} — transient failure that consumed the last attempt; record
 *       is terminal FAILED.</li>
 * </ul>
 */
public enum DeliveryOutcome {
    SUCCEEDED,
    FAILED_PERMANENT,
    RETRY_SCHEDULED,
    FAILED_RETRY_EXHAUSTED
}
