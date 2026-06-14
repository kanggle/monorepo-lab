package com.example.shipping.infrastructure.event;

import java.util.List;

/**
 * Cross-project fulfillment-intent message (ADR-MONO-022 §D7 forward leg).
 *
 * <p>Emitted in the <b>wms envelope convention (camelCase)</b> so the wms
 * outbound-service consumes it with its existing parser unchanged. This is the
 * Anti-Corruption Layer output shape — see
 * {@code specs/contracts/events/fulfillment-events.md}. The whole envelope is
 * stored as the outbox row {@code payload}.
 */
public record FulfillmentRequestedMessage(
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
            String customerPartnerCode,
            String warehouseCode,
            String requiredShipDate,
            ShipTo shipTo,
            List<Line> lines
    ) {}

    public record ShipTo(
            String recipientName,
            String address,
            String phone
    ) {}

    public record Line(
            int lineNo,
            String skuCode,
            String lotNo,
            int qtyOrdered
    ) {}
}
