package com.example.fanplatform.membership.application.exception;

/**
 * Thrown when a supplied {@code tier} value is not in
 * {@code { MEMBERS_ONLY, PREMIUM }}. Mapped to 422 {@code MEMBERSHIP_TIER_INVALID}.
 */
public class MembershipTierInvalidException extends RuntimeException {
    public MembershipTierInvalidException(String rawTier) {
        super("Invalid membership tier: " + rawTier);
    }
}
