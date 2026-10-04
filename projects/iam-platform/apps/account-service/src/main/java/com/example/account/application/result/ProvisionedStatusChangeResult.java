package com.example.account.application.result;

import java.time.Instant;

/**
 * TASK-BE-231: Result of a tenant-scoped account status change via the internal provisioning API.
 * Includes tenantId to distinguish from the generic {@link StatusChangeResult}.
 *
 * <p>TASK-BE-622 — {@code scope} is the {@link StatusChangeResult} vocabulary: {@code ACCOUNT} (the account's
 * own status changed) or {@code SITE_MEMBERSHIP} (the target is a consumer-pool member found through the path
 * site, and only that site's membership changed — {@code previousStatus} / {@code currentStatus} are then the
 * MEMBERSHIP's, and {@code tenantId} is that site).
 */
public record ProvisionedStatusChangeResult(
        String accountId,
        String tenantId,
        String previousStatus,
        String currentStatus,
        Instant changedAt,
        String scope
) {
    /** The account's own status changed ({@code scope = ACCOUNT}). */
    public ProvisionedStatusChangeResult(String accountId, String tenantId, String previousStatus,
                                         String currentStatus, Instant changedAt) {
        this(accountId, tenantId, previousStatus, currentStatus, changedAt, GdprDeleteResult.SCOPE_ACCOUNT);
    }

    /** TASK-BE-622 — only {@code siteTenantId}'s membership of a pool account changed; the account is untouched. */
    public static ProvisionedStatusChangeResult siteMembership(String accountId, String siteTenantId,
                                                               String previousMembershipStatus,
                                                               String currentMembershipStatus,
                                                               Instant changedAt) {
        return new ProvisionedStatusChangeResult(accountId, siteTenantId, previousMembershipStatus,
                currentMembershipStatus, changedAt, GdprDeleteResult.SCOPE_SITE_MEMBERSHIP);
    }
}
