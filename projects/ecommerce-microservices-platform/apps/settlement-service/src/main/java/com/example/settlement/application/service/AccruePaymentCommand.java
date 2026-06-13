package com.example.settlement.application.service;

import java.time.Instant;

/**
 * Command to accrue commission for a captured payment. The tenant + per-line seller
 * are joined from the cached snapshot (the payment event carries neither, AC-7).
 */
public record AccruePaymentCommand(String orderId, String paymentId, Instant occurredAt) {
}
