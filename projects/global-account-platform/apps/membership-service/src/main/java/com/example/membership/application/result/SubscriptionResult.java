package com.example.membership.application.result;

import com.example.membership.domain.plan.PlanLevel;
import com.example.membership.domain.subscription.Subscription;
import com.example.membership.domain.subscription.status.SubscriptionStatus;

import java.time.LocalDateTime;

public record SubscriptionResult(
        String subscriptionId,
        String accountId,
        PlanLevel planLevel,
        SubscriptionStatus status,
        LocalDateTime startedAt,
        LocalDateTime expiresAt,
        LocalDateTime cancelledAt
) {
    public static SubscriptionResult from(Subscription s) {
        return new SubscriptionResult(
                s.getId(),
                s.getAccountId(),
                s.getPlanLevel(),
                s.getStatus(),
                s.getStartedAt(),
                s.getExpiresAt(),
                s.getCancelledAt()
        );
    }
}
