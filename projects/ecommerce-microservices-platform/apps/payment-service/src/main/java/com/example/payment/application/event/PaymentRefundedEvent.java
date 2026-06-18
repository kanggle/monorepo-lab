package com.example.payment.application.event;

import com.example.payment.domain.model.Payment;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Outbox event envelope for a processed payment refund.
 *
 * <p>TASK-BE-400 (ADR-MONO-030 Step 4 facet c): {@code tenant_id} added as a
 * top-level envelope field alongside the existing standard fields. Consumers
 * that do not yet read {@code tenant_id} are unaffected (additive change).
 */
public record PaymentRefundedEvent(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("occurred_at") String occurredAt,
        String source,
        @JsonProperty("tenant_id") String tenantId,
        Payload payload
) {
    public record Payload(
            String paymentId,
            String orderId,
            String userId,
            long amount,
            String refundedAt
    ) {}

    public static PaymentRefundedEvent from(Payment payment) {
        return new PaymentRefundedEvent(
                UUID.randomUUID().toString(),
                "PaymentRefunded",
                Instant.now().toString(),
                "payment-service",
                payment.getTenantId(),
                new Payload(
                        payment.getPaymentId(),
                        payment.getOrderId(),
                        payment.getUserId(),
                        payment.getAmount(),
                        payment.getRefundedAt().toInstant(ZoneOffset.UTC).toString()
                )
        );
    }
}
