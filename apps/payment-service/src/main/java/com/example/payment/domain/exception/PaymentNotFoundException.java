package com.example.payment.domain.exception;

public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException(String orderId) {
        super("결제를 찾을 수 없습니다: orderId=" + orderId);
    }
}
