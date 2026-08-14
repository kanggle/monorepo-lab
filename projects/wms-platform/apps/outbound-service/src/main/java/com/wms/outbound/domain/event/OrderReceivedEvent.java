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
 *
 * <p>{@code tenantId} is additive (ADR-MONO-065 § D2, TASK-BE-583): the owning
 * customer tenant of the order, taken from the persisted aggregate — which
 * ADR-MONO-064 § D1 stamps from the creating caller's <em>signed</em> JWT, or from
 * the inbound ecommerce envelope for {@code FULFILLMENT_ECOMMERCE} orders. It rides
 * the outer envelope (not the payload), like the two return-leg events.
 *
 * <p>Why this event needed it: <b>this is the only point at which admin-service can
 * learn an order's tenant.</b> Its projections had no tenant axis at all, so the
 * order/shipment dashboards served every tenant's rows unfiltered while the raw REST
 * plane isolated them — the defect ADR-MONO-065 closes. The projection records this
 * value verbatim; it must not re-derive the tenant by looking the order up, which
 * would create a second, divergent source of truth.
 *
 * <p>{@code null} when the order has no tenant: created by an unrestricted caller
 * (Kafka consumer / scheduler / no security context), or predating ADR-MONO-064.
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
        String tenantId,
        Instant occurredAt,
        String actorId
) implements OutboundDomainEvent {

    /**
     * Backward-compatible constructor — B2B order, no drop-ship recipient
     * ({@code shipTo == null}) and no tenant ({@code tenantId == null}).
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
                warehouseId, requiredShipDate, null, lines, null, occurredAt, actorId);
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
