package com.example.payment.adapter.in.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Inbound event record mirroring order-service OrderPlaced contract.
 * See specs/contracts/events/order-events.md
 *
 * <p>TASK-BE-400: {@code tenant_id} added to the envelope mirror. Order events have
 * carried this field since TASK-BE-357 (ADR-MONO-030 Step 2 M5). The consumer now
 * threads it into {@link com.example.payment.domain.tenant.TenantContext} before
 * delegating to the application service, so the created Payment row inherits the
 * correct tenant (multi-tenant-ready). A missing/null field falls back to the
 * default tenant via TenantContext.
 */
public record OrderPlacedEvent(
        @JsonProperty("event_id") @JsonAlias("eventId") String eventId,
        @JsonProperty("event_type") @JsonAlias("eventType") String eventType,
        @JsonProperty("occurred_at") @JsonAlias("occurredAt") String occurredAt,
        String source,
        @JsonProperty("tenant_id") @JsonAlias("tenantId") String tenantId,
        OrderPlacedPayload payload
) {
    public record OrderPlacedPayload(
            String orderId,
            String userId,
            long totalPrice,
            List<OrderItem> items,
            ShippingAddress shippingAddress
    ) {}

    public record OrderItem(
            String productId,
            String variantId,
            int quantity,
            long unitPrice
    ) {}

    public record ShippingAddress(
            String recipient,
            String phone,
            String zipCode,
            String address1,
            String address2
    ) {}
}
