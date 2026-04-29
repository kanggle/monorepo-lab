package com.wms.outbound.domain.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code outbound.packing.completed} — published when all PackingUnits for an
 * order are SEALED and the sum of {@code PackingUnitLine.qty} equals every
 * order line's {@code qtyOrdered}. Order transitions to {@code PACKED}, saga
 * to {@code PACKING_CONFIRMED}.
 *
 * <p>Schema: {@code specs/contracts/events/outbound-events.md} §6.
 */
public record PackingCompletedEvent(
        UUID orderId,
        String orderNo,
        UUID warehouseId,
        Instant completedAt,
        List<Unit> packingUnits,
        int totalCartonCount,
        Integer totalWeightGrams,
        Instant occurredAt,
        String actorId
) implements OutboundDomainEvent {

    public record Unit(
            UUID packingUnitId,
            String cartonNo,
            String packingType,
            Integer weightGrams,
            Integer lengthMm,
            Integer widthMm,
            Integer heightMm,
            List<Line> lines
    ) {}

    public record Line(
            UUID orderLineId,
            UUID skuId,
            UUID lotId,
            int qty
    ) {}

    @Override
    public UUID aggregateId() {
        return orderId;
    }

    @Override
    public String aggregateType() {
        return "order";
    }

    @Override
    public String eventType() {
        return "outbound.packing.completed";
    }

    @Override
    public String partitionKey() {
        return orderId.toString();
    }
}
