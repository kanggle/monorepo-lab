package com.example.web.exception;

public class AccessDeniedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AccessDeniedException() {
        super("Insufficient permissions to access resource");
    }

    public AccessDeniedException(String message) {
        super(message);
    }
}
