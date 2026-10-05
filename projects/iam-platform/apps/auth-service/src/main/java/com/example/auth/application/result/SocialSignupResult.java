package com.example.auth.application.result;

import com.example.auth.domain.tenant.TenantContext;

/**
 * Result from account-service social signup internal call.
 *
 * @param tenantId TASK-BE-617 (auth-to-account-social.md, additive) — the tenant the returned account's
 *                 row lives in. {@code consumer-pool} means account-service signed the person up into the
 *                 consumer pool; anything else (or absent — an older account-service) is the per-tenant
 *                 behaviour from before. A storage value: never a token claim.
 */
public record SocialSignupResult(
        String accountId,
        String accountStatus,
        boolean newAccount,
        String tenantId
) {

    /** The pre-TASK-BE-617 shape (no {@code tenantId}) — a per-tenant account. */
    public SocialSignupResult(String accountId, String accountStatus, boolean newAccount) {
        this(accountId, accountStatus, newAccount, null);
    }

    /** TASK-BE-617 — whether account-service answered with a consumer-pool account. */
    public boolean poolAccount() {
        return TenantContext.isConsumerPool(tenantId);
    }
}
