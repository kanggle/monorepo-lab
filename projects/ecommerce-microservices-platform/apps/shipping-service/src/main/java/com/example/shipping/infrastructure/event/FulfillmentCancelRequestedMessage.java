package com.example.shipping.infrastructure.event;

/**
 * Ecommerce-initiated outbound cancel request (ADR-MONO-022 D4 v2(d) —
 * {@code ecommerce.fulfillment.cancel-requested.v1}, TASK-BE-649).
 *
 * <p>Emitted in the <b>wms envelope convention (camelCase)</b> — the same ACL output shape as
 * {@link ManualShipConfirmRequestedMessage} — so the wms outbound-service
 * {@code FulfillmentCancelRequestedConsumer} reads it with its existing
 * {@code EventEnvelopeParser}. Field names are the contract's, exactly:
 * {@code specs/contracts/events/fulfillment-events.md} § Event 3. The whole envelope is the
 * outbox row {@code payload}; {@code orderNo == aggregateId == orderId} (D5 correlation key).
 * {@code tenantId} appears on both the envelope and the payload, with the same value (contract).
 */
public record FulfillmentCancelRequestedMessage(
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
            String tenantId,
            String reason,
            String requestedAt
    ) {}
}
