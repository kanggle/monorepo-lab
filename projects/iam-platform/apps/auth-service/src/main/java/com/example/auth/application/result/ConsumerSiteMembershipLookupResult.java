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
 */
public record ConsumerSiteMembershipLookupResult(
        String siteTenantId,
        boolean consumerSite,
        String siteTenantType,
        String membershipStatus,
        List<String> siteRoles) {

    /** The membership status that admits a token (contract § 1 — {@code ACTIVE} | {@code LEFT}). */
    public static final String ACTIVE = "ACTIVE";

    public ConsumerSiteMembershipLookupResult {
        siteRoles = siteRoles == null ? List.of() : List.copyOf(siteRoles);
    }

    /** {@code true} only for a consumer site on which the account holds an ACTIVE membership. */
    public boolean isActiveMember() {
        return consumerSite && ACTIVE.equals(membershipStatus);
    }
}
