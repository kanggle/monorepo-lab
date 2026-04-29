package com.wms.outbound.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Base type for outbound-service domain events. The
 * {@code EventEnvelopeSerializer} pattern-matches the concrete record to
 * serialise the JSON payload defined in
 * {@code specs/contracts/events/outbound-events.md}.
 *
 * <p>Permitted events:
 * <ul>
 *   <li>{@link OrderReceivedEvent}</li>
 *   <li>{@link OrderCancelledEvent}</li>
 *   <li>{@link PickingRequestedEvent}</li>
 *   <li>{@link PickingCancelledEvent}</li>
 *   <li>{@link PickingCompletedEvent}</li>
 *   <li>{@link PackingCompletedEvent}</li>
 *   <li>{@link ShippingConfirmedEvent}</li>
 * </ul>
 */
public sealed interface OutboundDomainEvent
        permits OrderReceivedEvent, OrderCancelledEvent,
                PickingRequestedEvent, PickingCancelledEvent,
                PickingCompletedEvent, PackingCompletedEvent,
                ShippingConfirmedEvent {

    UUID aggregateId();

    String aggregateType();

    String eventType();

    String partitionKey();

    Instant occurredAt();

    String actorId();
}
