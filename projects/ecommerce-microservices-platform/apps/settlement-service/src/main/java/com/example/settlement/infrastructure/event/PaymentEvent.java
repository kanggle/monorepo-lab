package com.example.settlement.infrastructure.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The {@code payment.payment.completed} / {@code payment.payment.refunded} envelope.
 * <b>Carries no {@code tenant_id}</b> (payment-service has not joined Step 2) — the
 * tenant is derived from the cached OrderPlaced snapshot (AC-7). Settlement reads
 * {@code event_id} (dedupe) + {@code payload.{orderId, paymentId, paidAt|refundedAt}}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentEvent(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("payload") Payload payload) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(String orderId, String paymentId, String paidAt, String refundedAt) {
    }
}
