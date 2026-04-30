package com.example.membership.domain.subscription;

import com.example.membership.domain.subscription.status.SubscriptionStatus;

import java.time.LocalDateTime;

/**
 * Append-only audit record of a subscription status transition.
 */
public record SubscriptionStatusHistoryEntry(
        String subscriptionId,
        String accountId,
        SubscriptionStatus fromStatus,
        SubscriptionStatus toStatus,
        String reason,
        String actorType,
        LocalDateTime occurredAt
) {
}
