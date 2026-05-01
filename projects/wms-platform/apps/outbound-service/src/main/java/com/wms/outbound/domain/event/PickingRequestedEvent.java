package com.wms.outbound.domain.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code outbound.picking.requested} — saga step 1 fired in the same TX as
 * new-order creation. Cross-service contract with {@code inventory-service}.
 *
 * <p>Schema: {@code specs/contracts/events/outbound-events.md} §3.
 *
 * <p>In the TASK-BE-037 scope there is no {@code PickingRequest} aggregate yet
 * (that arrives in TASK-BE-038), so {@code reservationId} equals
 * {@code sagaId} here. Once {@code PickingRequest} ships, the event will
 * carry {@code PickingRequest.id} as the reservation id.
 *
 * <p>{@code lines[].locationId} is null in v1 scope: the picking planner
 * (TASK-BE-038) assigns concrete source locations; until then the inventory
 * consumer must decide allocation itself.
 */
public record PickingRequestedEvent(
        UUID sagaId,
        UUID reservationId,
        UUID orderId,
        UUID warehouseId,
        List<Line> lines,
        Instant occurredAt,
        String actorId
) implements OutboundDomainEvent {

    public record Line(
            UUID orderLineId,
            UUID skuId,
            UUID lotId,
            UUID locationId,
            int qtyToReserve
    ) {}

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
        return "outbound.picking.requested";
    }

    @Override
    public String partitionKey() {
        return sagaId.toString();
    }
}
