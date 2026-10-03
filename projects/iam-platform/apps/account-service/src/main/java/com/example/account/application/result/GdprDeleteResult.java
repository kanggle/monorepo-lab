package com.example.account.application.result;

import java.time.Instant;

/**
 * Result of {@code POST /internal/accounts/{id}/gdpr-delete}.
 *
 * <p>TASK-BE-619 — {@code scope} says what was actually erased (admin-to-account.md § gdpr-delete):
 * <ul>
 *   <li>{@link #SCOPE_ACCOUNT} — the account: DELETED + PII masked ({@code status = DELETED},
 *       {@code emailHash}, {@code maskedAt} set; {@code siteTenantId = null}).</li>
 *   <li>{@link #SCOPE_SITE_MEMBERSHIP} — a site operator's request on a consumer-pool member: only that
 *       site's membership became LEFT (owner decision 2026-10-03 «자기 사이트 멤버십만»). The account is
 *       untouched: {@code status} is its unchanged status, {@code emailHash} / {@code maskedAt} are null,
 *       {@code siteTenantId} is the site.</li>
 * </ul>
 */
public record GdprDeleteResult(
        String accountId,
        String status,
        String emailHash,
        Instant maskedAt,
        String scope,
        String siteTenantId
) {
    public static final String SCOPE_ACCOUNT = "ACCOUNT";
    public static final String SCOPE_SITE_MEMBERSHIP = "SITE_MEMBERSHIP";

    /** The account itself was erased (the pre-619 meaning — every caller before 619 saw only this). */
    public GdprDeleteResult(String accountId, String status, String emailHash, Instant maskedAt) {
        this(accountId, status, emailHash, maskedAt, SCOPE_ACCOUNT, null);
    }

    /** TASK-BE-619 — only {@code siteTenantId}'s membership was ended; the account is untouched. */
    public static GdprDeleteResult siteMembershipLeft(String accountId, String accountStatus, String siteTenantId) {
        return new GdprDeleteResult(accountId, accountStatus, null, null, SCOPE_SITE_MEMBERSHIP, siteTenantId);
    }
}
