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
     * The owning customer tenant of the order, echoed onto the outer envelope.
     *
     * <p>Three events override this: {@link ShippingConfirmedEvent} and
     * {@link OrderCancelledEvent} (the cross-project return leg — ADR-MONO-022
     * facet d, TASK-MONO-296) and {@link OrderReceivedEvent} (ADR-MONO-065 § D2 —
     * the only point at which admin-service can learn an order's tenant). Every
     * other event returns {@code null} and the serializer omits the field
     * (additive).
     *
     * <p>🔴 <b>Not an opaque correlation any more.</b> Until ADR-MONO-064 this value
     * only ever came from an inbound ecommerce envelope and wms never read it back.
     * Since ADR-MONO-064 § D1/D2 it is <em>also</em> the caller's signed tenant for
     * REST-created orders and <em>is</em> the isolation axis wms filters rows by —
     * so it is now carried for {@code MANUAL} orders too, not just
     * {@code FULFILLMENT_ECOMMERCE} ones. {@code null} means the order genuinely has
     * no tenant (unrestricted caller, or predating ADR-MONO-064), never "not
     * applicable to this order type".
     */
    default String tenantId() {
        return null;
    }
}
