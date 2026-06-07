package com.wms.outbound.domain.event;

import com.wms.outbound.domain.model.ShipToAddress;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * {@code outbound.order.received} — published in the same TX as new-order
 * creation. Schema: {@code specs/contracts/events/outbound-events.md} §1.
 *
 * <p>{@code shipTo} is additive (ADR-MONO-022 D2-a): the B2C drop-ship
 * recipient for {@code FULFILLMENT_ECOMMERCE}-origin orders, {@code null}
 * for {@code MANUAL} / {@code WEBHOOK_ERP}. Existing consumers
 * (admin-service) ignore it.
 */
public record OrderReceivedEvent(
        UUID orderId,
        String orderNo,
        String source,
        UUID customerPartnerId,
        String customerPartnerCode,
        UUID warehouseId,
        LocalDate requiredShipDate,
        ShipToAddress shipTo,
        List<Line> lines,
        Instant occurredAt,
        String actorId
) implements OutboundDomainEvent {

    /**
     * Backward-compatible constructor — B2B order, no drop-ship recipient
     * ({@code shipTo == null}).
     */
    public OrderReceivedEvent(UUID orderId,
                              String orderNo,
                              String source,
                              UUID customerPartnerId,
                              String customerPartnerCode,
                              UUID warehouseId,
                              LocalDate requiredShipDate,
                              List<Line> lines,
                              Instant occurredAt,
                              String actorId) {
        this(orderId, orderNo, source, customerPartnerId, customerPartnerCode,
                warehouseId, requiredShipDate, null, lines, occurredAt, actorId);
    }

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
