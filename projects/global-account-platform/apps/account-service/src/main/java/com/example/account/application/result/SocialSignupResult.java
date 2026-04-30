package com.example.account.application.result;

import com.example.account.domain.account.Account;

public record SocialSignupResult(
        String accountId,
        String email,
        String status,
        boolean created
) {
    public static SocialSignupResult fromExisting(Account account) {
        return new SocialSignupResult(
                account.getId(),
                account.getEmail(),
                account.getStatus().name(),
                false
        );
    }

    public static SocialSignupResult fromNew(Account account) {
        return new SocialSignupResult(
                account.getId(),
                account.getEmail(),
                account.getStatus().name(),
                true
        );
    }
}
