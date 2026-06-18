package com.example.payment.application.event;

import com.example.payment.domain.model.Payment;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Outbox event envelope for a successfully captured payment.
 *
 * <p>TASK-BE-400 (ADR-MONO-030 Step 4 facet c): {@code tenant_id} added as a
 * top-level envelope field alongside the existing standard fields. Consumers
 * that do not yet read {@code tenant_id} are unaffected (additive change).
 * settlement-service can now read the tenant directly from the event instead
 * of deriving it from the {@code OrderPlaced} snapshot (see marketplace-settlement.md §1).
 */
public record PaymentCompletedEvent(
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
            String paidAt
    ) {}

    public static PaymentCompletedEvent from(Payment payment) {
        return new PaymentCompletedEvent(
                UUID.randomUUID().toString(),
                "PaymentCompleted",
                Instant.now().toString(),
                "payment-service",
                payment.getTenantId(),
                new Payload(
                        payment.getPaymentId(),
                        payment.getOrderId(),
                        payment.getUserId(),
                        payment.getAmount(),
                        payment.getPaidAt().toInstant(ZoneOffset.UTC).toString()
                )
        );
    }
}
