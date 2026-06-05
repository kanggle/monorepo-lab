package com.example.account.presentation.dto.response;

import com.example.account.application.result.VerifyEmailResult;

import java.time.Instant;

/**
 * Response body for {@code POST /api/accounts/signup/verify-email} (TASK-BE-114).
 *
 * <p>Carries the freshly-stamped {@code emailVerifiedAt} so the client can
 * update its UI without an extra round-trip.</p>
 */
public record VerifyEmailResponse(
        String accountId,
        Instant emailVerifiedAt
) {

    public static VerifyEmailResponse from(VerifyEmailResult result) {
        return new VerifyEmailResponse(result.accountId(), result.emailVerifiedAt());
    }
}
