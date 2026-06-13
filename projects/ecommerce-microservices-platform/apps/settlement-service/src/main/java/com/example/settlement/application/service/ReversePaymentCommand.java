package com.example.settlement.application.service;

import java.time.Instant;

/**
 * Command to reverse an order's accruals on refund. v1 = full reversal of the
 * order's accruals (partial/proportional clawback is forward-declared).
 */
public record ReversePaymentCommand(String orderId, String paymentId, Instant occurredAt) {
}
