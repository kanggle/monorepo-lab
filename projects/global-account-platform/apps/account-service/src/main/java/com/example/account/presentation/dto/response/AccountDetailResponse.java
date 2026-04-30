package com.example.account.presentation.dto.response;

import com.example.account.application.result.AccountDetailResult;

import java.time.Instant;

public record AccountDetailResponse(
        String id,
        String email,
        String status,
        Instant createdAt,
        Profile profile
) {
    public record Profile(
            String displayName,
            String phoneMasked
    ) {}

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return null;
        return "*".repeat(phone.length() - 4) + phone.substring(phone.length() - 4);
    }

    public static AccountDetailResponse of(AccountDetailResult result) {
        Profile p = result.profile() == null ? null
                : new Profile(result.profile().displayName(), maskPhone(result.profile().phoneNumber()));
        return new AccountDetailResponse(
                result.id(), result.email(), result.status(), result.createdAt(), p);
    }
}
