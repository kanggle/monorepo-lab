package com.example.payment.application.event;

import com.example.payment.domain.model.Payment;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

public record PaymentRefundedEvent(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("occurred_at") String occurredAt,
        String source,
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
