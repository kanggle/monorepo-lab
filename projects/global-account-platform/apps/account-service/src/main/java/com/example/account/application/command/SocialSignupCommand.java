package com.example.account.application.command;

public record SocialSignupCommand(
        String email,
        String provider,
        String providerUserId,
        String displayName
) {
}
