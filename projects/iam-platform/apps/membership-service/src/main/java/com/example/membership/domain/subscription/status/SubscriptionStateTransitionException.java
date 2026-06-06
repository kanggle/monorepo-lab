package com.example.membership.domain.subscription.status;

public class SubscriptionStateTransitionException extends RuntimeException {

    private final SubscriptionStatus from;
    private final SubscriptionStatus to;

    public SubscriptionStateTransitionException(SubscriptionStatus from, SubscriptionStatus to) {
        super("Subscription status transition not allowed: " + from + " -> " + to);
        this.from = from;
        this.to = to;
    }

    public SubscriptionStatus getFrom() {
        return from;
    }

    public SubscriptionStatus getTo() {
        return to;
    }
}
