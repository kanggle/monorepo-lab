package com.example.product.infrastructure.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Inbound event DTOs for the reservation saga consumers (TASK-BE-428). order-service and
 * payment-service both publish the standard ENVELOPE shape ({@code event_id}/{@code event_type}/
 * {@code tenant_id}/{@code payload}) — verified against {@code order-events.md} /
 * {@code payment-events.md} and the producers' outbox serialization — so these DTOs mirror that
 * nested envelope (unlike the FLAT IAM {@code account.status.changed} consumer). Only the fields
 * product-service reads are mapped; everything else is ignored for additive forward-compat.
 */
public final class ReservationInboundEvents {

    private ReservationInboundEvents() {
    }

    /** {@code order.order.placed} — line snapshot to reserve. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrderPlacedMessage(
            @JsonProperty("event_id") String eventId,
            @JsonProperty("tenant_id") String tenantId,
            OrderPlacedPayload payload) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record OrderPlacedPayload(String orderId, List<Item> items) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Item(String productId, String variantId, int quantity) {}
    }

    /** {@code payment.payment.completed} — carries {@code orderId} only (no line items). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentCompletedMessage(
            @JsonProperty("event_id") String eventId,
            @JsonProperty("tenant_id") String tenantId,
            PaymentCompletedPayload payload) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record PaymentCompletedPayload(String orderId) {}
    }

    /** {@code order.order.cancelled} — release the reservation. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrderCancelledMessage(
            @JsonProperty("event_id") String eventId,
            @JsonProperty("tenant_id") String tenantId,
            OrderCancelledPayload payload) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record OrderCancelledPayload(String orderId) {}
    }
}
