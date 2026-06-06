package com.example.account.application.result;

import com.example.account.domain.account.Account;

import java.time.Instant;

public record SignupResult(
        String accountId,
        String email,
        String status,
        Instant createdAt
) {
    public static SignupResult from(Account account) {
        return new SignupResult(
                account.getId(),
                account.getEmail(),
                account.getStatus().name(),
                account.getCreatedAt()
        );
    }
}
