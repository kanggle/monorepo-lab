package com.example.fanplatform.membership.application.exception;

/**
 * Thrown when {@code renew} is called on a CANCELED membership. A cancel is a
 * deliberate opt-out, so it cannot be renewed — the user subscribes fresh
 * instead. Mapped to 422 {@code MEMBERSHIP_NOT_RENEWABLE}; NO row is created.
 */
public class MembershipNotRenewableException extends RuntimeException {
    public MembershipNotRenewableException(String membershipId) {
        super("Membership is not renewable (canceled): " + membershipId);
    }
}
