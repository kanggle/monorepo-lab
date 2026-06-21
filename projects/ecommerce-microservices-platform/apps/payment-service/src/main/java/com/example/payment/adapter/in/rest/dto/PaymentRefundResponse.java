package com.example.payment.adapter.in.rest.dto;

import com.example.payment.domain.model.Payment;

import java.time.LocalDateTime;

/**
 * Response for {@code POST /api/payments/{paymentId}/refund}. {@code amount} is the
 * captured payment total; {@code refundedAmount} is the cumulative refunded; {@code status}
 * is {@code PARTIALLY_REFUNDED} until the cumulative reaches the total, then {@code REFUNDED}.
 */
public record PaymentRefundResponse(
        String paymentId,
        String orderId,
        String userId,
        long amount,
        long refundedAmount,
        String status,
        LocalDateTime refundedAt
) {
    public static PaymentRefundResponse from(Payment payment) {
        return new PaymentRefundResponse(
                payment.getPaymentId(),
                payment.getOrderId(),
                payment.getUserId(),
                payment.getAmount(),
                payment.getRefundedAmount(),
                payment.getStatus().name(),
                payment.getRefundedAt()
        );
    }
}
