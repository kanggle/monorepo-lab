package com.example.notification.domain.exception;

public class UnauthorizedNotificationAccessException extends RuntimeException {
    public UnauthorizedNotificationAccessException(String message) {
        super(message);
    }
}
