package com.example.admin.application.exception;

/**
 * TASK-BE-343 (ADR-MONO-023 D3): account-service returned 409
 * {@code SUBSCRIPTION_ALREADY_EXISTS} on subscribe. Surfaced to the operator as
 * 409 {@code SUBSCRIPTION_ALREADY_EXISTS}.
 */
public class SubscriptionAlreadyExistsException extends RuntimeException {
    public SubscriptionAlreadyExistsException(String message) {
        super(message);
    }
}
