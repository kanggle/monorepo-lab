package com.example.shipping.infrastructure.event;

/**
 * Inbound wms event {@code outbound.shipping.confirmed} (cross-project return leg,
 * ADR-MONO-022 §D7). <b>wms envelope convention (camelCase)</b> — see
 * specs/contracts/events/wms-shipment-subscriptions.md.
 *
 * <p>{@code tenantId} is the additive envelope-level tenant correlation
 * (ADR-MONO-022 facet d, TASK-MONO-296) echoed by wms; the consumer binds it
 * into {@code TenantContext} (local-Shipping-row fallback when {@code null}).
 */
public record WmsShippingConfirmedEvent(
        String eventId,
        String eventType,
        String occurredAt,
        String aggregateType,
        String aggregateId,
        String tenantId,
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
