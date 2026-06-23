package com.example.order.infrastructure.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Inbound event record mirroring the product-service {@code OrderReservationFailed}
 * contract (TASK-BE-428). See specs/contracts/events/product-events.md.
 *
 * <p>Published by product-service on topic {@code product.product.reservation-failed}
 * when the payment-driven reservation saga could not all-or-nothing reserve stock for a
 * paid order (at least one line short) — no stock is decremented and the whole order is
 * held for backorder. order-service consumes this to transition the order
 * {@code PENDING → BACKORDERED}.
 *
 * <p>Envelope mirrors {@link StockChangedEvent}: snake_case {@code event_id} /
 * {@code event_type} / {@code occurred_at} / {@code tenant_id} with camelCase
 * {@code @JsonAlias} fallbacks, plus the owning-tenant envelope field bound into
 * {@code TenantContext} by the consumer.
 */
public record OrderReservationFailedEvent(
        @JsonProperty("event_id") @JsonAlias("eventId") String eventId,
        @JsonProperty("event_type") @JsonAlias("eventType") String eventType,
        @JsonProperty("occurred_at") @JsonAlias("occurredAt") String occurredAt,
        String source,
        @JsonProperty("tenant_id") @JsonAlias("tenantId") String tenantId,
        OrderReservationFailedPayload payload
) {
    public record OrderReservationFailedPayload(
            String orderId,
            String reason,
            List<Shortage> shortages
    ) {}

    public record Shortage(
            String variantId,
            int requested,
            int available
    ) {}
}
