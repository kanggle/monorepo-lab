package com.example.membership.application.exception;

public class SubscriptionNotFoundException extends RuntimeException {
    public SubscriptionNotFoundException(String id) {
        super("Subscription not found: " + id);
    }
}
