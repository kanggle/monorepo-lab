package com.example.membership.application.result;

import com.example.membership.domain.plan.PlanLevel;

public record AccessCheckResult(
        String accountId,
        PlanLevel requiredPlanLevel,
        boolean allowed,
        PlanLevel activePlanLevel
) {
}
