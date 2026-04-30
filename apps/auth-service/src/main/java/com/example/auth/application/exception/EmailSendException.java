package com.example.auth.application.exception;

public class EmailSendException extends RuntimeException {

    public EmailSendException(String message, Throwable cause) {
        super(message, cause);
    }

    public EmailSendException(String message) {
        super(message);
    }
}
