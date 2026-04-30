package com.example.membership.application.command;

import com.example.membership.domain.plan.PlanLevel;

public record ActivateSubscriptionCommand(
        String accountId,
        PlanLevel planLevel,
        String idempotencyKey
) {
}
