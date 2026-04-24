package com.example.payment.application.service;

import java.time.LocalDateTime;

public record PaymentConfirmResult(
        String paymentId,
        String orderId,
        String status,
        String paymentMethod,
        String receiptUrl,
        LocalDateTime paidAt
) {
}
