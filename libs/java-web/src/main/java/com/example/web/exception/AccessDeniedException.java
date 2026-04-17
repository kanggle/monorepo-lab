package com.example.web.exception;

public class AccessDeniedException extends RuntimeException {

    public AccessDeniedException() {
        super("Insufficient permissions to access resource");
    }

    public AccessDeniedException(String message) {
        super(message);
    }
}
