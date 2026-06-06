package com.example.membership.presentation.dto;

import com.example.membership.application.result.AccessCheckResult;

public record AccessCheckResponse(
        String accountId,
        String requiredPlanLevel,
        boolean allowed,
        String activePlanLevel
) {
    public static AccessCheckResponse from(AccessCheckResult r) {
        return new AccessCheckResponse(
                r.accountId(),
                r.requiredPlanLevel().name(),
                r.allowed(),
                r.activePlanLevel().name()
        );
    }
}
