package com.example.admin.application.exception;

/**
 * TASK-BE-343 (ADR-MONO-023 D3): account-service returned 404
 * {@code SUBSCRIPTION_NOT_FOUND} on a subscription transition. Surfaced to the
 * operator as 404 {@code SUBSCRIPTION_NOT_FOUND}.
 */
public class SubscriptionNotFoundException extends RuntimeException {
    public SubscriptionNotFoundException(String message) {
        super(message);
    }
}
