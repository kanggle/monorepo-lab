package com.wms.outbound.domain.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * {@code outbound.order.received} — published in the same TX as new-order
 * creation. Schema: {@code specs/contracts/events/outbound-events.md} §1.
 */
public record OrderReceivedEvent(
        UUID orderId,
        String orderNo,
        String source,
        UUID customerPartnerId,
        String customerPartnerCode,
        UUID warehouseId,
        LocalDate requiredShipDate,
        List<Line> lines,
        Instant occurredAt,
        String actorId
) implements OutboundDomainEvent {

    public record Line(
            UUID orderLineId,
            int lineNo,
            UUID skuId,
            String skuCode,
            UUID lotId,
            int qtyOrdered
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
        return "outbound.order.received";
    }

    @Override
    public String partitionKey() {
        return orderId.toString();
    }
}
