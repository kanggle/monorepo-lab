package com.example.account.presentation.dto.response;

import com.example.account.application.result.SocialSignupResult;

/**
 * @param tenantId TASK-BE-617 (auth-to-account-social.md) — additive: the account row's tenant,
 *                 {@code consumer-pool} for a pool account.
 */
public record SocialSignupResponse(
        String accountId,
        String email,
        String status,
        String tenantId
) {
    public static SocialSignupResponse from(SocialSignupResult result) {
        return new SocialSignupResponse(
                result.accountId(),
                result.email(),
                result.status(),
                result.tenantId()
        );
    }
}
