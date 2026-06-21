package com.example.settlement.application.service;

import java.time.Instant;

/**
 * Command to reverse an order's accruals on refund. {@code refundAmount} is the amount of
 * THIS refund (minor units), used for the proportional clawback fraction
 * ({@code refundAmount / accruedGross}). {@code fullyRefunded} marks the final refund that
 * closed the payment out — it reverses the exact remaining per accrual so the order nets to
 * exactly zero, absorbing any partial-rounding drift.
 */
public record ReversePaymentCommand(String orderId, String paymentId, long refundAmount,
                                    boolean fullyRefunded, Instant occurredAt) {
}
