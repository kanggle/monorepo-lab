package com.example.account.presentation.dto.response;

import com.example.account.application.result.LeaveConsumerSiteResult;

import java.time.Instant;

/** TASK-BE-619 — response of {@code DELETE /api/accounts/me/site-membership} (account-api.md). */
public record LeaveConsumerSiteResponse(
        String accountId,
        String siteTenantId,
        String membershipStatus,
        String leftBy,
        Instant leftAt) {

    public static LeaveConsumerSiteResponse from(LeaveConsumerSiteResult result) {
        return new LeaveConsumerSiteResponse(
                result.accountId(), result.siteTenantId(), result.membershipStatus(),
                result.leftBy(), result.leftAt());
    }
}
