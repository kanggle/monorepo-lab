package com.wms.outbound.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code outbound.picking.cancelled} — compensation event triggered when an
 * order is cancelled while the saga still holds an active reservation
 * ({@code RESERVED}, {@code PICKING_CONFIRMED}, or {@code PACKING_CONFIRMED}).
 *
 * <p>Schema: {@code specs/contracts/events/outbound-events.md} §4.
 */
public record PickingCancelledEvent(
        UUID sagaId,
        UUID reservationId,
        UUID orderId,
        String reason,
        Instant occurredAt,
        String actorId
) implements OutboundDomainEvent {

    @Override
    public UUID aggregateId() {
        return sagaId;
    }

    @Override
    public String aggregateType() {
        return "outbound_saga";
    }

    @Override
    public String eventType() {
        return "outbound.picking.cancelled";
    }

    @Override
    public String partitionKey() {
        return sagaId.toString();
    }
}
