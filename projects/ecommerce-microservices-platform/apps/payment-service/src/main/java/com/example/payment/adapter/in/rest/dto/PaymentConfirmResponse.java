package com.example.payment.adapter.in.rest.dto;

import com.example.payment.application.service.PaymentConfirmResult;

import java.time.LocalDateTime;

public record PaymentConfirmResponse(
        String paymentId,
        String orderId,
        String status,
        String paymentMethod,
        String receiptUrl,
        LocalDateTime paidAt
) {
    public static PaymentConfirmResponse from(PaymentConfirmResult result) {
        return new PaymentConfirmResponse(
                result.paymentId(),
                result.orderId(),
                result.status(),
                result.paymentMethod(),
                result.receiptUrl(),
                result.paidAt()
        );
    }
}
