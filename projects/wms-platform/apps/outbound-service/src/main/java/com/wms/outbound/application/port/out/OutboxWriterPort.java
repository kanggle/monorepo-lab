package com.wms.outbound.application.port.out;

import com.wms.outbound.domain.event.OutboundDomainEvent;

/**
 * Out-port for the {@code outbound_outbox} table writer (T3 transactional
 * outbox).
 *
 * <p>Implementations declare {@code @Transactional(propagation = MANDATORY)}
 * so the outbox row joins the use-case's TX. There is no separate publish
 * step here — that's the {@code OutboxPublisher} background job.
 */
public interface OutboxWriterPort {

    /**
     * Serialise the event into the canonical envelope shape and persist a
     * {@code PENDING} outbox row in the caller's transaction.
     */
    void publish(OutboundDomainEvent event);
}
