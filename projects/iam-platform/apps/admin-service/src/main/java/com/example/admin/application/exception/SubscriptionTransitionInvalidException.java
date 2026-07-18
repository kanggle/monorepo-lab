package com.example.admin.application.exception;

/**
 * TASK-BE-343 (ADR-MONO-023 D1/D3): account-service returned 409
 * {@code SUBSCRIPTION_TRANSITION_INVALID} (the SubscriptionStatus state-machine
 * guard rejected the transition). Surfaced to the operator as 409
 * {@code SUBSCRIPTION_TRANSITION_INVALID}.
 */
public class SubscriptionTransitionInvalidException extends AccountBusinessException {
    public SubscriptionTransitionInvalidException(String message) {
        super(message);
    }
}
