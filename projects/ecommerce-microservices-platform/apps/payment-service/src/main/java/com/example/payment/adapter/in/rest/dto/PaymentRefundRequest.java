package com.example.payment.adapter.in.rest.dto;

/**
 * Request body for {@code POST /api/payments/{paymentId}/refund}. {@code amount} is the
 * minor-unit amount to refund this call (partial). Validation lives in the domain
 * ({@code Payment.refund(amount)}): {@code amount ≤ 0} or {@code > remaining refundable}
 * raises {@code InvalidPaymentException} → HTTP 400 {@code INVALID_PAYMENT_REQUEST}. Keeping
 * the single domain validation path means a non-positive amount returns 400 (not a generic
 * bean-validation 500 — payment-service has no MethodArgumentNotValidException handler).
 */
public record PaymentRefundRequest(
        long amount
) {
}
