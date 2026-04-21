package com.example.payment.application.exception;

public class AmountMismatchException extends RuntimeException {
    public AmountMismatchException(long expected, long actual) {
        super("결제 금액이 일치하지 않습니다: expected=" + expected + ", actual=" + actual);
    }
}
