package com.example.fanplatform.membership.application.exception;

/**
 * Thrown when no ACTIVE billing-key enrollment exists for the caller + tier (e.g.
 * a cancel with nothing to cancel). Mapped to 404 {@code BILLING_KEY_ENROLLMENT_NOT_FOUND}.
 */
public class BillingKeyEnrollmentNotFoundException extends RuntimeException {
    public BillingKeyEnrollmentNotFoundException(String tier) {
        super("No active billing-key enrollment for tier: " + tier);
    }
}
