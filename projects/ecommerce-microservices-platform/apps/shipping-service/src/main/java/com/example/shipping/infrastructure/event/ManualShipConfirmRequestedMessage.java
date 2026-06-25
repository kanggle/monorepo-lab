package com.example.shipping.infrastructure.event;

/**
 * Operator-driven manual ship-confirm message (ADR-MONO-022 D4 v2(c) —
 * {@code ecommerce.shipping.manual-confirm-requested.v1}).
 *
 * <p>Emitted in the <b>wms envelope convention (camelCase)</b> — same ACL output
 * shape as {@link FulfillmentRequestedMessage} — so the wms outbound-service consumes
 * it with its existing {@code EventEnvelopeParser} unchanged. See
 * {@code specs/contracts/events/fulfillment-events.md} § Event 2. The whole envelope
 * is stored as the outbox row {@code payload}; {@code orderNo == aggregateId == orderId}
 * (FulfillmentAcl invariant, D5 correlation key).
 */
public record ManualShipConfirmRequestedMessage(
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
            String carrierCode,
            String trackingNo
    ) {}
}
