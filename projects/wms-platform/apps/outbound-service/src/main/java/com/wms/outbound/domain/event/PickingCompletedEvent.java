package com.wms.outbound.domain.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code outbound.picking.completed} — published when the operator confirms
 * all picks for the order ({@code ConfirmPickingService}). At this point the
 * saga has advanced to {@code PICKING_CONFIRMED} and the order to {@code PICKED}.
 *
 * <p>Schema: {@code specs/contracts/events/outbound-events.md} §5.
 *
 * <p>Operational event — admin-service consumes it for KPI projections.
 * Inventory does NOT consume it (reserved stock stays reserved until
 * {@code outbound.shipping.confirmed}).
 */
public record PickingCompletedEvent(
        UUID sagaId,
        UUID orderId,
        UUID pickingConfirmationId,
        String confirmedBy,
        Instant confirmedAt,
        List<Line> lines,
        Instant occurredAt,
        String actorId
) implements OutboundDomainEvent {

    public record Line(
            UUID orderLineId,
            UUID skuId,
            UUID lotId,
            UUID actualLocationId,
            int qtyConfirmed
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
        return "outbound.picking.completed";
    }

    @Override
    public String partitionKey() {
        return sagaId.toString();
    }
}
