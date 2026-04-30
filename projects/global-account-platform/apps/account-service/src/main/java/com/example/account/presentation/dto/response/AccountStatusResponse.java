package com.example.account.presentation.dto.response;

import com.example.account.application.result.AccountStatusResult;

import java.time.Instant;

public record AccountStatusResponse(
        String accountId,
        String status,
        Instant statusChangedAt,
        String reason
) {
    public static AccountStatusResponse from(AccountStatusResult result) {
        return new AccountStatusResponse(
                result.accountId(),
                result.status(),
                result.statusChangedAt(),
                result.reason()
        );
    }
}
