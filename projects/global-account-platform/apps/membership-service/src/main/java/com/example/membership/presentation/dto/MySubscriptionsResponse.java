package com.example.membership.presentation.dto;

import com.example.membership.application.result.MySubscriptionsResult;

import java.util.List;

public record MySubscriptionsResponse(
        String accountId,
        List<SubscriptionResponse> subscriptions,
        String activePlanLevel
) {
    public static MySubscriptionsResponse from(MySubscriptionsResult r) {
        return new MySubscriptionsResponse(
                r.accountId(),
                r.subscriptions().stream().map(SubscriptionResponse::from).toList(),
                r.activePlanLevel().name()
        );
    }
}
