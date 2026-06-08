package com.example.order.infrastructure.event;

/**
 * Inbound wms event {@code outbound.order.cancelled} (cross-project backorder/cancel
 * path, ADR-MONO-022 §D4 v2(a), TASK-MONO-197). <b>wms envelope convention (camelCase)</b>
 * — see specs/contracts/events/wms-shipment-subscriptions.md.
 *
 * <p>order-service consumes this (group {@code order-service-wms}, distinct from the
 * shipping-service consumer) to auto-cancel + refund the order; {@code payload.orderNo}
 * is the ecommerce orderId (ADR-022 §D5 correlation).
 */
public record WmsOutboundCancelledEvent(
        String eventId,
        String eventType,
        String occurredAt,
        String aggregateType,
        String aggregateId,
        Payload payload
) {
    public record Payload(
            String orderNo,
            String previousStatus,
            String reason,
            String cancelledAt
    ) {}
}
