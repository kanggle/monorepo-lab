package com.example.account.presentation.dto.response;

import com.example.account.application.result.ConsumerSiteMembershipResult;

import java.util.List;

/**
 * TASK-BE-615 — response of {@code GET /internal/tenants/{tenantId}/consumer-members/{accountId}}
 * (auth-to-account.md). Always 200: a non-member, a non-pool account and a non-consumer site are
 * answers in the body ({@code membershipStatus = null} / {@code consumerSite = false}), not 404s.
 *
 * <p>TASK-BE-619 — {@code leftBy} ({@code SELF} | {@code OPERATOR} | {@code null}) says who made a
 * {@code LEFT} membership LEFT; auth-service reopens the consent screen only for {@code SELF}.
 */
public record ConsumerSiteMembershipResponse(
        String accountId,
        String siteTenantId,
        boolean consumerSite,
        String siteTenantType,
        String membershipStatus,
        List<String> siteRoles,
        String leftBy) {

    public static ConsumerSiteMembershipResponse from(ConsumerSiteMembershipResult result) {
        return new ConsumerSiteMembershipResponse(
                result.accountId(), result.siteTenantId(), result.consumerSite(),
                result.siteTenantType(), result.membershipStatus(), result.siteRoles(), result.leftBy());
    }
}
