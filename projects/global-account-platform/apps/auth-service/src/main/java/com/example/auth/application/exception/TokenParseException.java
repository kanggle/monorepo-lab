package com.example.auth.application.exception;

public class TokenParseException extends RuntimeException {

    public TokenParseException(String message, Throwable cause) {
        super(message, cause);
    }

    public TokenParseException(String message) {
        super(message);
    }
}
