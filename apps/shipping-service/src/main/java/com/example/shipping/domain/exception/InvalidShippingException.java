package com.example.shipping.domain.exception;

public class InvalidShippingException extends RuntimeException {

    public InvalidShippingException(String message) {
        super(message);
    }
}
