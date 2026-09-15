package com.example.settlement.infrastructure.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The {@code order.order.placed} envelope as consumed by settlement (snake_case
 * ecommerce convention). Settlement reads only the contracted fields:
 * {@code event_id} (dedupe), {@code tenant_id} (the authoritative tenant source,
 * AC-7), and {@code payload.orderId} + {@code payload.items[].{unitPrice,quantity,sellerId}}
 * + the additive {@code payload.couponId} / {@code payload.discountAmount} (TASK-BE-592; absent on
 * orders without a coupon and on events from an older order-service).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderPlacedEvent(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("tenant_id") String tenantId,
        @JsonProperty("payload") Payload payload) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(String orderId, List<Item> items, String couponId, Long discountAmount) {

        /** An order without coupon fields. */
        public Payload(String orderId, List<Item> items) {
            this(orderId, items, null, null);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(long unitPrice, int quantity, String sellerId) {
    }
}
