package com.example.promotion.interfaces.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Inbound event record mirroring order-service OrderCancelled contract.
 * See specs/contracts/events/order-events.md
 */
public record OrderCancelledEvent(
        @JsonProperty("event_id") @JsonAlias("eventId") String eventId,
        @JsonProperty("event_type") @JsonAlias("eventType") String eventType,
        @JsonProperty("occurred_at") @JsonAlias("occurredAt") String occurredAt,
        String source,
        // Outer-axis tenant on the envelope (ADR-MONO-030 M5; TASK-BE-368). order-service
        // stamps it (OrderCancelledEvent.of → TenantContext.currentTenant); a
        // pre-multi-tenant producer omits it → null → default tenant (net-zero, D8).
        @JsonProperty("tenant_id") @JsonAlias("tenantId") String tenantId,
        OrderCancelledPayload payload
) {
    public record OrderCancelledPayload(
            String orderId,
            String userId,
            String cancelledAt
    ) {}
}
