package com.example.fanplatform.community.domain.membership;

/**
 * Port for verifying whether an account holds membership / subscription that
 * grants access to a {@code MEMBERS_ONLY} or {@code PREMIUM} post.
 *
 * <p>Implementations MUST be fail-closed: on any infrastructure error, return
 * {@code false} (deny access). For the v1 PREMIUM tier the
 * {@code AlwaysAllowMembershipChecker} default returns {@code true} +
 * WARN-level log; a real membership-service consumer is a v2 concern (see
 * TASK-FAN-BE-002 § Edge Cases — PREMIUM v1).
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
