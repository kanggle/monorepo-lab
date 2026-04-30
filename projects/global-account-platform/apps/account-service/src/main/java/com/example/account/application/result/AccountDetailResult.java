package com.example.account.application.result;

import java.time.Instant;

public record AccountDetailResult(
        String id,
        String email,
        String status,
        Instant createdAt,
        Profile profile
) {
    public record Profile(
            String displayName,
            String phoneNumber
    ) {}
}
