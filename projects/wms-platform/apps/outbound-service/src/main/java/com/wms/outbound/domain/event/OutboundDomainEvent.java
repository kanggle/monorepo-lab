package com.wms.outbound.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Base type for outbound-service domain events. The
 * {@code EventEnvelopeSerializer} pattern-matches the concrete record to
 * serialise the JSON payload defined in
 * {@code specs/contracts/events/outbound-events.md}.
 *
 * <p>Permitted events for TASK-BE-037 scope:
 * <ul>
 *   <li>{@link OrderReceivedEvent}</li>
 *   <li>{@link OrderCancelledEvent}</li>
 *   <li>{@link PickingRequestedEvent}</li>
 *   <li>{@link PickingCancelledEvent}</li>
 * </ul>
 *
 * <p>Picking-completed / packing-completed / shipping-confirmed land in
 * TASK-BE-038.
 */
public sealed interface OutboundDomainEvent
        permits OrderReceivedEvent, OrderCancelledEvent,
                PickingRequestedEvent, PickingCancelledEvent {

    UUID aggregateId();

    String aggregateType();

    String eventType();

    String partitionKey();

    Instant occurredAt();

    String actorId();
}
