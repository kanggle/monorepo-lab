package com.example.order.application.event;

import com.example.order.domain.tenant.TenantContext;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public record OrderCancelledEvent(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("occurred_at") String occurredAt,
        String source,
        @JsonProperty("tenant_id") String tenantId,
        Payload payload
) {
    public static OrderCancelledEvent of(String orderId, String userId,
                                          Instant cancelledAt, Clock clock) {
        // Envelope tenant (M5). System-initiated cancels (backorder / withdrawal)
        // run on the order's tenant via the saga path; HTTP user-cancel uses the
        // request context. Unset → default tenant (net-zero, D8).
        return new OrderCancelledEvent(
                UUID.randomUUID().toString(),
                "OrderCancelled",
                Instant.now(clock).toString(),
                "order-service",
                TenantContext.currentTenant(),
                new Payload(orderId, userId, cancelledAt.toString())
        );
    }

    public record Payload(String orderId, String userId, String cancelledAt) {}
}
