package com.example.finance.account.application.view;

import com.example.finance.account.domain.account.Account;

import java.time.Instant;
import java.util.List;

/**
 * Read model for an account + its balances. {@code ownerRef} is intentionally
 * omitted (regulated PII — never returned, F7).
 */
public record AccountView(String accountId,
                          String status,
                          String currency,
                          String kycLevel,
                          List<BalanceView> balances,
                          Instant createdAt,
                          Instant updatedAt) {

    public static AccountView of(Account a, List<BalanceView> balances) {
        return new AccountView(
                a.getId(),
                a.getStatus().name(),
                a.getCurrency().code(),
                a.getKycLevel().name(),
                balances,
                a.getCreatedAt(),
                a.getUpdatedAt());
    }
}
