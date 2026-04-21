package com.example.shipping.application.exception;

public class UnauthorizedShippingAccessException extends RuntimeException {

    public UnauthorizedShippingAccessException(String message) {
        super(message);
    }
}
