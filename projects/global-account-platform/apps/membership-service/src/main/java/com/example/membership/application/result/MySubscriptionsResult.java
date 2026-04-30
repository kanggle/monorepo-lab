package com.example.membership.application.result;

import com.example.membership.domain.plan.PlanLevel;

import java.util.List;

public record MySubscriptionsResult(
        String accountId,
        List<SubscriptionResult> subscriptions,
        PlanLevel activePlanLevel
) {
}
