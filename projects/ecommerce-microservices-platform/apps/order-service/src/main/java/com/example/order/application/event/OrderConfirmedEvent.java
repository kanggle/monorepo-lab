package com.example.order.application.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Domain event published when an order transitions to {@code CONFIRMED}.
 *
 * <p>Envelope stays in the ecommerce-internal snake-ish shape
 * ({@code event_id}/{@code event_type}/{@code occurred_at}), consumed by
 * shipping-service. The payload additively carries {@code lines} (per-line
 * SKU + quantity) and {@code shippingAddress} so shipping-service can build the
 * cross-project fulfillment-intent event (ADR-MONO-022 §D7) without a
 * synchronous call back to order-service.
 *
 * <p>See {@code specs/contracts/events/order-events.md}.
 */
public record OrderConfirmedEvent(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("occurred_at") String occurredAt,
        String source,
        Payload payload
) {
    public static OrderConfirmedEvent of(String orderId, String userId, Instant confirmedAt,
                                         List<Line> lines, ShippingAddress shippingAddress,
                                         Clock clock) {
        return new OrderConfirmedEvent(
                UUID.randomUUID().toString(),
                "OrderConfirmed",
                Instant.now(clock).toString(),
                "order-service",
                new Payload(orderId, userId, confirmedAt.toString(), lines, shippingAddress)
        );
    }

    public record Payload(
            String orderId,
            String userId,
            String confirmedAt,
            List<Line> lines,
            ShippingAddress shippingAddress
    ) {}

    /** One ordered line. {@code sku} = ecommerce sellable-unit id (variantId, else productId). */
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
