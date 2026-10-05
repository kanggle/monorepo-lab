package com.example.account.application.result;

import com.example.account.domain.account.Account;

/**
 * @param tenantId TASK-BE-617 — the tenant the returned account's row lives in ({@code consumer-pool}
 *                 for a pool account). auth-service writes the social-identity row under it and builds a
 *                 pool principal when it is the pool; a storage value, never a token claim.
 */
public record SocialSignupResult(
        String accountId,
        String email,
        String status,
        boolean created,
        String tenantId
) {
    public static SocialSignupResult fromExisting(Account account) {
        return new SocialSignupResult(
                account.getId(),
                account.getEmail(),
                account.getStatus().name(),
                false,
                account.getTenantId().value()
        );
    }

    public static SocialSignupResult fromNew(Account account) {
        return new SocialSignupResult(
                account.getId(),
                account.getEmail(),
                account.getStatus().name(),
                true,
                account.getTenantId().value()
        );
    }
}
