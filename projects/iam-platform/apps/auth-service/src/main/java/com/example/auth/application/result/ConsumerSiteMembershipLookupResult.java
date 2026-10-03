package com.example.auth.application.result;

import java.util.List;

/**
 * TASK-BE-615 — account-service's answer to «may this pool account use this consumer site, and with
 * which site roles» ({@code GET /internal/tenants/{site}/consumer-members/{accountId}},
 * auth-to-account.md). Used by the form login (pool-first only on a consumer site), the authorize
 * gate and token issuance (multi-tenancy.md § 소비자 계정 풀 § 4).
 *
 * @param siteTenantId     the site asked about
 * @param consumerSite     the site is a consumer site ({@code B2C_CONSUMER}, not the pool itself)
 * @param siteTenantType   the site's authoritative {@code tenant_type} (the token's {@code tenant_type}),
 *                         {@code null} when the tenant does not exist
 * @param membershipStatus {@code ACTIVE} / {@code LEFT}, or {@code null} for no membership
 * @param siteRoles        the account's roles on THAT site outside the seed (never another site's);
 *                         empty unless the membership is ACTIVE. Never null
 * @param leftBy           TASK-BE-619 — for a {@code LEFT} membership, who left: {@code SELF} | {@code OPERATOR};
 *                         {@code null} otherwise (and from an account-service older than 619, which never
 *                         wrote LEFT)
 */
public record ConsumerSiteMembershipLookupResult(
        String siteTenantId,
        boolean consumerSite,
        String siteTenantType,
        String membershipStatus,
        List<String> siteRoles,
        String leftBy) {

    /** The membership status that admits a token (contract § 1 — {@code ACTIVE} | {@code LEFT}). */
    public static final String ACTIVE = "ACTIVE";
    /** TASK-BE-619 — {@code consumer_site_memberships.status} of a membership that ended. */
    public static final String LEFT = "LEFT";
    /** TASK-BE-619 — {@code leftBy} of a membership the person ended themself (reopenable by consent). */
    public static final String LEFT_BY_SELF = "SELF";

    public ConsumerSiteMembershipLookupResult {
        siteRoles = siteRoles == null ? List.of() : List.copyOf(siteRoles);
    }

    /** An answer without a leave record (every answer that is not a LEFT membership). */
    public ConsumerSiteMembershipLookupResult(String siteTenantId, boolean consumerSite, String siteTenantType,
                                              String membershipStatus, List<String> siteRoles) {
        this(siteTenantId, consumerSite, siteTenantType, membershipStatus, siteRoles, null);
    }

    /** {@code true} only for a consumer site on which the account holds an ACTIVE membership. */
    public boolean isActiveMember() {
        return consumerSite && ACTIVE.equals(membershipStatus);
    }

    /**
     * TASK-BE-619 (owner decision 2026-10-03 «다시 동의하면 복귀») — the account left this consumer site
     * THEMSELF, so the consent screen may bring it back. A membership the site's operator ended
     * ({@code OPERATOR}), or a LEFT one without a recorded actor, is not reopenable — the conservative side.
     */
    public boolean isReopenableByConsent() {
        return consumerSite && LEFT.equals(membershipStatus) && LEFT_BY_SELF.equals(leftBy);
    }
}
