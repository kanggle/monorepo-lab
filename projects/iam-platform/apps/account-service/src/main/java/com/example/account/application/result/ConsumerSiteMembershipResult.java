package com.example.account.application.result;

import java.util.List;

/**
 * TASK-BE-615 — what auth-service needs to sign a pool account into one consumer site
 * (multi-tenancy.md § 소비자 계정 풀 § 4): is the site a consumer site at all, the site's
 * {@code tenant_type}, the account's membership status there, and its site roles outside the seed.
 *
 * @param accountId        the account asked about
 * @param siteTenantId     the site asked about
 * @param consumerSite     {@code true} when the site is a consumer site ({@code B2C_CONSUMER}, not
 *                         the pool itself). {@code false} also for a tenant that does not exist
 * @param siteTenantType   the site's {@code tenant_type}, or {@code null} when the tenant does not exist
 * @param membershipStatus {@code ACTIVE} / {@code LEFT}, or {@code null} when the account is not a
 *                         pool account or has no membership row on that site
 * @param siteRoles        {@code consumer_site_roles} on THAT site — only when the membership is
 *                         ACTIVE, otherwise empty. Never another site's roles
 * @param leftBy           TASK-BE-619 — for a {@code LEFT} membership, who made it LEFT ({@code SELF} |
 *                         {@code OPERATOR}); {@code null} otherwise. Only {@code SELF} is reopened by
 *                         consent (owner decision 2026-10-03)
 */
public record ConsumerSiteMembershipResult(
        String accountId,
        String siteTenantId,
        boolean consumerSite,
        String siteTenantType,
        String membershipStatus,
        List<String> siteRoles,
        String leftBy) {

    /** A result without a leave record — every answer that is not a LEFT membership. */
    public ConsumerSiteMembershipResult(String accountId, String siteTenantId, boolean consumerSite,
                                        String siteTenantType, String membershipStatus, List<String> siteRoles) {
        this(accountId, siteTenantId, consumerSite, siteTenantType, membershipStatus, siteRoles, null);
    }
}
