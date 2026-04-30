package com.example.membership.application.exception;

public class SubscriptionNotActiveException extends RuntimeException {
    public SubscriptionNotActiveException(String id) {
        super("Subscription is not ACTIVE: " + id);
    }
}
