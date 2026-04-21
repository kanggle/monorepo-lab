package com.example.order.application.exception;

public class UnauthorizedOrderAccessException extends RuntimeException {

    public UnauthorizedOrderAccessException() {
        super("Unauthorized access to the order");
    }
}
