package com.example.fanplatform.community.domain.membership;

/**
 * Port for verifying whether an account holds membership / subscription that
 * grants access to a {@code MEMBERS_ONLY} or {@code PREMIUM} post.
 *
 * <p>Implementations MUST be fail-closed: on any infrastructure error, return
 * {@code false} (deny access). The production implementation is
 * {@code HttpMembershipChecker} (wired by {@code MembershipCheckerAutoConfig}),
 * which enforces both {@code MEMBERS_ONLY} and {@code PREMIUM} tiers against
 * membership-service (TASK-FAN-BE-010 hard fail-close; feed path completed in
 * TASK-FAN-BE-019). {@code AlwaysAllowMembershipChecker} is a
 * {@code @ConditionalOnMissingBean} escape-hatch fallback only (e.g. tests that
 * opt out), never selected when the HTTP checker is present.
 */
public interface MembershipChecker {

    /**
     * @param accountId   the caller's account id (sub claim)
     * @param tier        either {@code MEMBERS_ONLY} or {@code PREMIUM}
     * @param tenantId    tenant scope
     * @return {@code true} when the account has the required tier; {@code false}
     *         to deny access (fail-closed)
     */
    boolean hasAccess(String accountId, String tier, String tenantId);
}
