package com.example.shipping.infrastructure.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Inbound event record mirroring order-service OrderConfirmed contract.
 * See specs/contracts/events/order-events.md
 *
 * <p>The {@code lines} + {@code shippingAddress} payload fields (ADR-MONO-022 §D7)
 * are additive: they feed the cross-project fulfillment forward leg. They may be
 * absent on a pre-enrichment producer, in which case the fulfillment event is
 * published with empty lines / null shipTo.
 */
public record OrderConfirmedEvent(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("occurred_at") String occurredAt,
        String source,
        @JsonProperty("tenant_id") String tenantId,
        OrderConfirmedPayload payload
) {
    public record OrderConfirmedPayload(
            String orderId,
            String userId,
            String confirmedAt,
            List<Line> lines,
            ShippingAddress shippingAddress
    ) {}

    public record Line(
            String sku,
            String productId,
            String variantId,
            int quantity
    ) {}

    public record ShippingAddress(
            String recipientName,
            String address,
            String phone
    ) {}
}
