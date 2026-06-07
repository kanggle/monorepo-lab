package com.example.shipping.infrastructure.event;

/**
 * Inbound wms event {@code outbound.order.cancelled} (cross-project backorder/cancel
 * path, ADR-MONO-022 §D4). <b>wms envelope convention (camelCase)</b> — see
 * specs/contracts/events/wms-shipment-subscriptions.md.
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
