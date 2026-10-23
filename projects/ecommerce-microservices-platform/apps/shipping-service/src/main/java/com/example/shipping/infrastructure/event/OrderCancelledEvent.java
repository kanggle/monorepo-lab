package com.example.shipping.infrastructure.event;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Inbound event record mirroring the order-service {@code OrderCancelled} contract
 * ({@code specs/contracts/events/order-events.md} § OrderCancelled, topic
 * {@code order.order.cancelled}). Ecommerce-internal snake_case envelope, like
 * {@link OrderConfirmedEvent}. shipping-service reads only {@code orderId} and
 * {@code cancelledAt}; {@code userId} / {@code cancelReason} are carried for logging and
 * for parity with the contract (TASK-BE-649).
 */
public record OrderCancelledEvent(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("occurred_at") String occurredAt,
        String source,
        @JsonProperty("tenant_id") String tenantId,
        OrderCancelledPayload payload
) {
    public record OrderCancelledPayload(
            String orderId,
            String userId,
            String cancelledAt,
            String cancelReason
    ) {}
}
