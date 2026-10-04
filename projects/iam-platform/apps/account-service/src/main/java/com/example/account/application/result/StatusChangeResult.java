package com.example.account.application.result;

import java.time.Instant;

/**
 * Result of a status change ({@code POST /internal/accounts/{id}/lock} · {@code /unlock}, …).
 *
 * <p>TASK-BE-621 — {@code scope} mirrors {@link DeleteAccountResult}: {@code ACCOUNT} (the account's own
 * status changed) or {@code SITE_MEMBERSHIP} (a site operator's lock / unlock of a consumer-pool member
 * changed only that site's membership — {@code previousStatus} / {@code currentStatus} are then the
 * MEMBERSHIP's, and {@code siteTenantId} is the site).
 */
public record StatusChangeResult(
        String accountId,
        String previousStatus,
        String currentStatus,
        Instant changedAt,
        String scope,
        String siteTenantId
) {
    public StatusChangeResult(String accountId, String previousStatus, String currentStatus, Instant changedAt) {
        this(accountId, previousStatus, currentStatus, changedAt, GdprDeleteResult.SCOPE_ACCOUNT, null);
    }

    /** TASK-BE-621 — only {@code siteTenantId}'s membership changed; the account is untouched. */
    public static StatusChangeResult siteMembership(String accountId, String previousMembershipStatus,
                                                    String currentMembershipStatus, Instant changedAt,
                                                    String siteTenantId) {
        return new StatusChangeResult(accountId, previousMembershipStatus, currentMembershipStatus, changedAt,
                GdprDeleteResult.SCOPE_SITE_MEMBERSHIP, siteTenantId);
    }
}
