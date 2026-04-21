package com.example.payment.application.exception;

public class PaymentAlreadyCompletedException extends RuntimeException {
    public PaymentAlreadyCompletedException(String orderId) {
        super("이미 완료된 결제입니다: orderId=" + orderId);
    }
}
