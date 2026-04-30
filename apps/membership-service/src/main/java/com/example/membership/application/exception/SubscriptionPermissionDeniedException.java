package com.example.membership.application.exception;

public class SubscriptionPermissionDeniedException extends RuntimeException {
    public SubscriptionPermissionDeniedException(String message) {
        super(message);
    }
}
