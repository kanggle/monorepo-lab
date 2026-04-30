package com.example.membership.application.result;

/**
 * Result of an activation call: carries whether the request was a fresh activation
 * (201 Created) or the cached response for an idempotent replay (200 OK).
 */
public record ActivateSubscriptionResult(SubscriptionResult subscription, boolean created) {

    public static ActivateSubscriptionResult created(SubscriptionResult s) {
        return new ActivateSubscriptionResult(s, true);
    }

    public static ActivateSubscriptionResult replayed(SubscriptionResult s) {
        return new ActivateSubscriptionResult(s, false);
    }
}
