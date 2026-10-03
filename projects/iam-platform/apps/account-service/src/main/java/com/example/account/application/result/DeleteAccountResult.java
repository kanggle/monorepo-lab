package com.example.account.application.result;

import java.time.Instant;

/**
 * Result of an account delete ({@code DELETE /api/accounts/me}, {@code POST /internal/accounts/{id}/delete}).
 *
 * <p>TASK-BE-619 — {@code scope} mirrors {@link GdprDeleteResult}: {@code ACCOUNT} (the account entered the
 * DELETED grace period) or {@code SITE_MEMBERSHIP} (a site operator's delete of a consumer-pool member ended
 * only that site's membership — {@code currentStatus} is the account's unchanged status,
 * {@code gracePeriodEndsAt} is null, {@code siteTenantId} is the site).
 */
public record DeleteAccountResult(
        String accountId,
        String previousStatus,
        String currentStatus,
        Instant gracePeriodEndsAt,
        String scope,
        String siteTenantId
) {
    public DeleteAccountResult(String accountId, String previousStatus, String currentStatus,
                               Instant gracePeriodEndsAt) {
        this(accountId, previousStatus, currentStatus, gracePeriodEndsAt, GdprDeleteResult.SCOPE_ACCOUNT, null);
    }

    /** TASK-BE-619 — only {@code siteTenantId}'s membership was ended; the account is untouched. */
    public static DeleteAccountResult siteMembershipLeft(String accountId, String accountStatus, String siteTenantId) {
        return new DeleteAccountResult(accountId, accountStatus, accountStatus, null,
                GdprDeleteResult.SCOPE_SITE_MEMBERSHIP, siteTenantId);
    }
}
