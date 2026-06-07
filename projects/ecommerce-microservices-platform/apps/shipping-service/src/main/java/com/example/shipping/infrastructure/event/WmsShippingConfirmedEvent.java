package com.example.shipping.infrastructure.event;

/**
 * Inbound wms event {@code outbound.shipping.confirmed} (cross-project return leg,
 * ADR-MONO-022 §D7). <b>wms envelope convention (camelCase)</b> — see
 * specs/contracts/events/wms-shipment-subscriptions.md.
 */
public record WmsShippingConfirmedEvent(
        String eventId,
        String eventType,
        String occurredAt,
        String aggregateType,
        String aggregateId,
        Payload payload
) {
    public record Payload(
            String orderId,
            String orderNo,
            String shipmentNo,
            String carrierCode,
            String shippedAt
    ) {}
}
