package com.example.order.application.event;

import com.example.order.domain.tenant.TenantContext;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public record OrderSagaRecoveryExhaustedEvent(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("occurred_at") String occurredAt,
        String source,
        @JsonProperty("tenant_id") String tenantId,
        Payload payload
) {
    public static OrderSagaRecoveryExhaustedEvent of(String orderId, String userId,
                                                     String lastState, int attemptCount,
                                                     Instant placedAt, Instant lastTransitionAt,
                                                     String failureReason, Clock clock) {
        // Envelope tenant (M5). The stuck-detector sweep is a global operational
        // path with no request context, so this system alert resolves to the
        // default tenant unless a tenant context is bound (net-zero, D8).
        return new OrderSagaRecoveryExhaustedEvent(
                UUID.randomUUID().toString(),
                "OrderSagaRecoveryExhausted",
                Instant.now(clock).toString(),
                "order-service",
                TenantContext.currentTenant(),
                new Payload(orderId, userId, lastState, attemptCount,
                        placedAt.toString(), lastTransitionAt.toString(), failureReason)
        );
    }

    public record Payload(
            String orderId,
            String userId,
            String lastState,
            int attemptCount,
            String placedAt,
            String lastTransitionAt,
            String failureReason
    ) {}
}
