package com.example.payment.application.exception;

public class PgConfirmFailedException extends RuntimeException {
    public PgConfirmFailedException(String message) {
        super("PG 결제 승인 실패: " + message);
    }

    public PgConfirmFailedException(String message, Throwable cause) {
        super("PG 결제 승인 실패: " + message, cause);
    }
}
