package com.example.fanplatform.membership.domain.membership.status;

/**
 * Thrown when a membership status transition is rejected by
 * {@link MembershipStateMachine}.
 *
 * <p>Mapped to HTTP 422 {@code MEMBERSHIP_STATE_INVALID} by the controller
 * advice. Carries the {@code from} and {@code to} so the API layer can surface a
 * useful error message. Note: an idempotent re-cancel of a CANCELED membership
 * is NOT an error — it is a no-op handled before the state machine is consulted.
 */
public class InvalidStateTransitionException extends RuntimeException {

    private final MembershipStatus from;
    private final MembershipStatus to;

    public InvalidStateTransitionException(MembershipStatus from, MembershipStatus to) {
        super("Invalid membership status transition: " + from + " -> " + to);
        this.from = from;
        this.to = to;
    }

    public MembershipStatus from() {
        return from;
    }

    public MembershipStatus to() {
        return to;
    }
}
