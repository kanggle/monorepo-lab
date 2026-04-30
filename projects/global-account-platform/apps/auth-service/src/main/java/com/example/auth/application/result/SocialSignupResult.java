package com.example.auth.application.result;

/**
 * Result from account-service social signup internal call.
 */
public record SocialSignupResult(
        String accountId,
        String accountStatus,
        boolean newAccount
) {
}
