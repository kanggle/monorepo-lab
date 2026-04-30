package com.example.membership.domain.subscription.status;

public enum SubscriptionStatus {
    /**
     * Virtual starting state for new subscriptions; never persisted.
     */
    NONE,
    ACTIVE,
    EXPIRED,
    CANCELLED
}
