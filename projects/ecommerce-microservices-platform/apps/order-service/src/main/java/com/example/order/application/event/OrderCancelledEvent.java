package com.example.order.application.event;

import com.example.order.domain.model.CancelReason;
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
    /**
     * Back-compat factory (TASK-BE-435): defaults {@code cancelReason} to
     * {@link CancelReason#OPERATOR}. Pre-existing callers that did not distinguish a
     * system-timeout cancel keep emitting {@code cancelReason = "OPERATOR"}, which is
     * exactly the cancel cause those paths represent (user / operator / backorder /
     * withdrawal).
     */
    public static OrderCancelledEvent of(String orderId, String userId,
                                          Instant cancelledAt, Clock clock) {
        return of(orderId, userId, cancelledAt, CancelReason.OPERATOR, clock);
    }

    public static OrderCancelledEvent of(String orderId, String userId,
                                          Instant cancelledAt, CancelReason cancelReason,
                                          Clock clock) {
        // Envelope tenant (M5). System-initiated cancels (backorder / withdrawal /
        // stuck-detector) run on the order's tenant via the saga path; HTTP user-cancel
        // uses the request context. Unset → default tenant (net-zero, D8).
        CancelReason reason = cancelReason != null ? cancelReason : CancelReason.OPERATOR;
        return new OrderCancelledEvent(
                UUID.randomUUID().toString(),
                "OrderCancelled",
                Instant.now(clock).toString(),
                "order-service",
                TenantContext.currentTenant(),
                new Payload(orderId, userId, cancelledAt.toString(), reason.name())
        );
    }

    /**
     * {@code cancelReason} (TASK-BE-435) — {@code "OPERATOR" | "PAYMENT_TIMEOUT"}. Additive /
     * back-compatible: a consumer reading a legacy event without it MUST treat the cancel as
     * {@code "OPERATOR"} (mirrors the {@code PaymentRefunded.fullyRefunded} back-compat note).
     */
    public record Payload(String orderId, String userId, String cancelledAt, String cancelReason) {}
}
