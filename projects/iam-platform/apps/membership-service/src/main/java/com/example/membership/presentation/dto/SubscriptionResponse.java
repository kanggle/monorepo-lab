package com.example.membership.presentation.dto;

import com.example.membership.application.result.SubscriptionResult;

import java.time.ZoneOffset;

public record SubscriptionResponse(
        String subscriptionId,
        String accountId,
        String planLevel,
        String status,
        String startedAt,
        String expiresAt,
        String cancelledAt
) {
    public static SubscriptionResponse from(SubscriptionResult r) {
        return new SubscriptionResponse(
                r.subscriptionId(),
                r.accountId(),
                r.planLevel().name(),
                r.status().name(),
                r.startedAt() == null ? null : r.startedAt().toInstant(ZoneOffset.UTC).toString(),
                r.expiresAt() == null ? null : r.expiresAt().toInstant(ZoneOffset.UTC).toString(),
                r.cancelledAt() == null ? null : r.cancelledAt().toInstant(ZoneOffset.UTC).toString()
        );
    }
}
