package com.example.admin.application.exception;

public class OperatorUnauthorizedException extends RuntimeException {
    public OperatorUnauthorizedException(String message) {
        super(message);
    }
}
