package com.example.payment.adapter.in.rest.dto;

import com.example.payment.domain.model.Payment;

import java.time.LocalDateTime;

public record PaymentDetailResponse(
        String paymentId,
        String orderId,
        String userId,
        long amount,
        String status,
        String paymentKey,
        String paymentMethod,
        String receiptUrl,
        LocalDateTime createdAt,
        LocalDateTime paidAt,
        LocalDateTime refundedAt
) {
    public static PaymentDetailResponse from(Payment payment) {
        return new PaymentDetailResponse(
                payment.getPaymentId(),
                payment.getOrderId(),
                payment.getUserId(),
                payment.getAmount(),
                payment.getStatus().name(),
                payment.getPaymentKey(),
                payment.getPaymentMethod(),
                payment.getReceiptUrl(),
                payment.getCreatedAt(),
                payment.getPaidAt(),
                payment.getRefundedAt()
        );
    }
}
