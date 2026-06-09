package com.example.fanplatform.membership.application.exception;

/**
 * Thrown when the PG mock declines authorization. Mapped to 422
 * {@code PAYMENT_DECLINED} — NO membership row is created.
 */
public class PaymentDeclinedException extends RuntimeException {
    public PaymentDeclinedException() {
        super("Payment authorization was declined");
    }
}
