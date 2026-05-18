package com.example.finance.account.application.view;

import com.example.finance.account.domain.balance.Hold;

import java.time.Instant;

/** Read model for a hold. Money as minor-unit strings (F5). */
public record HoldView(String holdId,
                       String accountId,
                       String amountMinor,
                       String currency,
                       String status,
                       Instant expiresAt) {

    public static HoldView from(Hold h) {
        return new HoldView(
                h.getId(),
                h.getAccountId(),
                h.amount().toMinorString(),
                h.getCurrency().code(),
                h.getStatus().name(),
                h.getExpiresAt());
    }
}
