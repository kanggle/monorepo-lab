package com.example.fanplatform.membership.application.exception;

/**
 * Thrown when a membership is missing OR belongs to a different account/tenant.
 * Mapped to 404 {@code MEMBERSHIP_NOT_FOUND} — existence is not leaked.
 */
public class MembershipNotFoundException extends RuntimeException {
    public MembershipNotFoundException(String membershipId) {
        super("Membership not found: " + membershipId);
    }
}
