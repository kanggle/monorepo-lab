package com.wms.outbound.adapter.in.messaging.consumer;

/**
 * Strategy interface implemented by each master-aggregate projector
 * (warehouse / zone / location / sku / partner / lot).
 *
 * <p>One implementation per topic. Implementations parse their slice of
 * the {@link EventEnvelope#payload()} and delegate to
 * {@code MasterReadModelWriterPort} to upsert the local snapshot row.
 *
 * <p>Implementations must be {@code @Component} beans so {@link MasterEventConsumer}
 * can wire them by type.
 */
interface MasterEventProjector {

    /**
     * Apply the given envelope to the local read-model. Called inside the
     * {@code @Transactional} boundary opened by
     * {@link MasterEventConsumer}'s {@code @KafkaListener} methods, after
     * dedupe has admitted the event.
     */
    void apply(EventEnvelope envelope);
}
