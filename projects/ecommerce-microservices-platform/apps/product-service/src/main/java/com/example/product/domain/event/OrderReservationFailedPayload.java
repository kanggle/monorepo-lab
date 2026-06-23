package com.example.product.domain.event;

import java.util.List;

/**
 * Payload of {@code OrderReservationFailed} (TASK-BE-428, topic
 * {@code product.product.reservation-failed}). Published by the payment-driven
 * reservation saga when an all-or-nothing reserve could not be satisfied because at
 * least one line was short — per the all-or-nothing rule <b>no stock was decremented</b>
 * and the whole order is held for backorder.
 *
 * <p>{@code shortages} lists only the lines that were short at reservation time
 * (informational; the order is backordered as a whole regardless of how many lines were
 * short). Schema: {@code specs/contracts/events/product-events.md § OrderReservationFailed}.
 */
public record OrderReservationFailedPayload(
        String orderId,
        String reason,
        List<Shortage> shortages
) implements EventPayload {

    public record Shortage(String variantId, int requested, int available) {}
}
