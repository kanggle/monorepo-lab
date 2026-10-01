package com.example.account.presentation.dto.response;

import com.example.account.application.result.ConsumerSiteMembershipResult;

import java.util.List;

/**
 * TASK-BE-615 — response of {@code GET /internal/tenants/{tenantId}/consumer-members/{accountId}}
 * (auth-to-account.md). Always 200: a non-member, a non-pool account and a non-consumer site are
 * answers in the body ({@code membershipStatus = null} / {@code consumerSite = false}), not 404s.
 */
public record ConsumerSiteMembershipResponse(
        String accountId,
        String siteTenantId,
        boolean consumerSite,
        String siteTenantType,
        String membershipStatus,
        List<String> siteRoles) {

    public static ConsumerSiteMembershipResponse from(ConsumerSiteMembershipResult result) {
        return new ConsumerSiteMembershipResponse(
                result.accountId(), result.siteTenantId(), result.consumerSite(),
                result.siteTenantType(), result.membershipStatus(), result.siteRoles());
    }
}
