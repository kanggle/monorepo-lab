package com.wms.outbound.domain.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code outbound.shipping.confirmed} — saga step 4: instructs
 * {@code inventory-service} to consume reserved stock. Once published, the
 * saga is in {@code SHIPPED} and cannot be rolled back (v1).
 *
 * <p>Schema: {@code specs/contracts/events/outbound-events.md} §7.
 *
 * <p>Cross-service contract — jointly owned with {@code inventory-service}.
 *
 * <p>{@code orderNo} is additive (ADR-MONO-022 D5): the order business id —
 * for {@code FULFILLMENT_ECOMMERCE} orders this equals the ecommerce order id,
 * the correlation key the ecommerce return-leg consumer matches on. Additive ⇒
 * existing consumers (inventory-service, admin-service) ignore it.
 *
 * <p>{@code tenantId} is additive (ADR-MONO-022 facet d, TASK-MONO-296): the
 * opaque ecommerce-tenant correlation captured at intake and echoed back on the
 * outer envelope so the ecommerce return consumer re-binds the originating
 * tenant. {@code null} for B2B / standalone orders (then omitted from the
 * envelope). wms never interprets it.
 */
public record ShippingConfirmedEvent(
        UUID sagaId,
        UUID reservationId,
        UUID orderId,
        String orderNo,
        UUID shipmentId,
        String shipmentNo,
        UUID warehouseId,
        Instant shippedAt,
        String carrierCode,
        String tenantId,
        List<Line> lines,
        Instant occurredAt,
        String actorId
) implements OutboundDomainEvent {

    public record Line(
            UUID orderLineId,
            UUID skuId,
            UUID lotId,
            UUID locationId,
            int qtyConfirmed
    ) {}

    @Override
    public UUID aggregateId() {
        return shipmentId;
    }

    @Override
    public String aggregateType() {
        return "shipment";
    }

    @Override
    public String eventType() {
        return "outbound.shipping.confirmed";
    }

    @Override
    public String partitionKey() {
        return sagaId.toString();
    }
}
