package com.example.payment.adapter.in.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Inbound event record mirroring order-service OrderCancelled contract.
 * See specs/contracts/events/order-events.md
 *
 * <p>TASK-BE-400: {@code tenant_id} added to the envelope mirror. Order events have
 * carried this field since TASK-BE-357 (ADR-MONO-030 Step 2 M5). The consumer now
 * threads it into {@link com.example.payment.domain.tenant.TenantContext} before
 * delegating to the application service.
 */
public record OrderCancelledEvent(
        @JsonProperty("event_id") @JsonAlias("eventId") String eventId,
        @JsonProperty("event_type") @JsonAlias("eventType") String eventType,
        @JsonProperty("occurred_at") @JsonAlias("occurredAt") String occurredAt,
        String source,
        @JsonProperty("tenant_id") @JsonAlias("tenantId") String tenantId,
        OrderCancelledPayload payload
) {
    public record OrderCancelledPayload(
            String orderId,
            String userId,
            String cancelledAt,
            /**
             * TASK-BE-435: distinguishes a system stuck-detector cancel ({@code "PAYMENT_TIMEOUT"})
             * from an operator cancel ({@code "OPERATOR"}). Additive / back-compatible — a legacy
             * event without this field deserializes to {@code null}, treated as {@code "OPERATOR"}
             * (matching pre-change behaviour). The payment-service branch is independent of the
             * reason (both require money safety), so this field is informational here.
             */
            String cancelReason
    ) {
        /** Effective cancel reason, defaulting a null/legacy value to {@code "OPERATOR"}. */
        public String effectiveCancelReason() {
            return cancelReason == null ? "OPERATOR" : cancelReason;
        }
    }
}
