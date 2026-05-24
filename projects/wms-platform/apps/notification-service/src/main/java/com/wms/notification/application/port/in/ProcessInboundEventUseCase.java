package com.wms.notification.application.port.in;

import com.wms.notification.domain.alert.AlertEnvelope;

/**
 * Inbound port — every {@code @KafkaListener} consumer normalises its
 * source-specific envelope into an {@link AlertEnvelope} and calls this
 * port. The application service handles routing + dedupe + delivery row +
 * outbox row in one transaction.
 */
public interface ProcessInboundEventUseCase {

    /** Outcome surfaced back to the consumer for observability. */
    enum Outcome {
        /** A matching rule fired; one or more delivery rows were created. */
        QUEUED,
        /** Matcher predicate filtered the event. */
        FILTERED,
        /** No enabled rule for this eventType. */
        NO_RULE,
        /** Dedupe table indicated this eventId was already processed. */
        DUPLICATE
    }

    /**
     * Process a single inbound event. Idempotent on {@code eventId}.
     *
     * @return outcome describing what the routing service did
     */
    Outcome process(AlertEnvelope envelope);
}
