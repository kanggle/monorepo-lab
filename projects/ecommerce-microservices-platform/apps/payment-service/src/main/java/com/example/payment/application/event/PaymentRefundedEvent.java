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
        @JsonProperty("source") String source,
        @JsonProperty("tenant_id") String tenantId,
        Payload payload
) {
    public record Payload(
            String paymentId,
            String orderId,
            String userId,
            long amount,
            long totalRefunded,
            boolean fullyRefunded,
            String refundedAt
    ) {}

    /**
     * Builds the event for one refund. {@code refundAmount} is the amount of THIS
     * refund (a partial refund &lt; the captured total); {@code totalRefunded} +
     * {@code fullyRefunded} come from the payment's cumulative state after the refund.
     */
    public static PaymentRefundedEvent from(Payment payment, long refundAmount) {
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
                        refundAmount,
                        payment.getRefundedAmount(),
                        payment.isFullyRefunded(),
                        payment.getRefundedAt().toInstant(ZoneOffset.UTC).toString()
                )
        );
    }
}
