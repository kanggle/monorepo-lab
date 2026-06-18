package com.example.shipping.infrastructure.event;

/**
 * Inbound wms event {@code outbound.order.cancelled} (cross-project backorder/cancel
 * path, ADR-MONO-022 §D4). <b>wms envelope convention (camelCase)</b> — see
 * specs/contracts/events/wms-shipment-subscriptions.md.
 *
 * <p>{@code tenantId} is the additive envelope-level tenant correlation
 * (ADR-MONO-022 facet d, TASK-MONO-296) echoed by wms; consumers bind it into
 * {@code TenantContext} (local-row fallback when {@code null}).
 */
public record WmsOutboundCancelledEvent(
        String eventId,
        String eventType,
        String occurredAt,
        String aggregateType,
        String aggregateId,
        String tenantId,
        Payload payload
) {
    public record Payload(
            String orderNo,
            String previousStatus,
            String reason,
            String cancelledAt
    ) {}
}
