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
 *   <li>{@link SagaRecoveryExhaustedEvent}</li>
 * </ul>
 */
public sealed interface OutboundDomainEvent
        permits OrderReceivedEvent, OrderCancelledEvent,
                PickingRequestedEvent, PickingCancelledEvent,
                PickingCompletedEvent, PackingCompletedEvent,
                ShippingConfirmedEvent, SagaRecoveryExhaustedEvent {

    UUID aggregateId();

    String aggregateType();

    String eventType();

    String partitionKey();

    Instant occurredAt();

    String actorId();

    /**
     * Opaque ecommerce-tenant correlation echoed onto the outer envelope
     * (ADR-MONO-022 facet d, TASK-MONO-296). Only the cross-project return-leg
     * events ({@link ShippingConfirmedEvent}, {@link OrderCancelledEvent}) carry
     * one for {@code FULFILLMENT_ECOMMERCE}-origin orders; every other event and
     * all B2B / standalone orders return {@code null}, in which case the
     * serializer omits the field (additive). wms never interprets it.
     */
    default String tenantId() {
        return null;
    }
}
