package com.example.account.presentation.dto.response;

import com.example.account.application.result.SocialSignupResult;

public record SocialSignupResponse(
        String accountId,
        String email,
        String status
) {
    public static SocialSignupResponse from(SocialSignupResult result) {
        return new SocialSignupResponse(
                result.accountId(),
                result.email(),
                result.status()
        );
    }
}
