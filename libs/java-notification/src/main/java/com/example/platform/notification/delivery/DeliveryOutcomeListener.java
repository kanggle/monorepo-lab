package com.example.platform.notification.delivery;

/**
 * Optional callback the service can register to react to each terminal/retry
 * outcome of a dispatch — e.g. the wms reference re-emits
 * {@code wms.notification.delivered.v1} via its outbox on terminal outcomes.
 *
 * <p>The lib invokes this <b>after</b> the record has been transitioned and saved,
 * inside the same dispatch call (and therefore the service's
 * {@code @Transactional(REQUIRES_NEW)} bean), so a service can write its outbox row
 * in the same transaction. Keeping the emission service-side preserves HARDSTOP-03
 * (no domain event types in the lib).
 *
 * <p>This is a functional interface — a service may also simply act on the
 * {@link DeliveryOutcome} returned by {@link DeliveryDispatcher#dispatch(DeliveryRecord)}
 * and skip registering a listener.
 */
@FunctionalInterface
public interface DeliveryOutcomeListener {

    /** No-op listener for services that act on the returned {@link DeliveryOutcome} instead. */
    DeliveryOutcomeListener NOOP = (record, outcome) -> { };

    /**
     * @param record  the record after its transition + save
     * @param outcome the classified dispatch outcome
     */
    void onOutcome(DeliveryRecord record, DeliveryOutcome outcome);
}
