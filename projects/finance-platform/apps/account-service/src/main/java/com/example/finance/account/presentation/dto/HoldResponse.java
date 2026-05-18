package com.example.finance.account.presentation.dto;

import com.example.finance.account.application.view.HoldView;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record HoldResponse(String holdId,
                           String accountId,
                           MoneyResponse money,
                           String status,
                           Instant expiresAt,
                           String transactionId) {

    public static HoldResponse from(HoldView v, String transactionId) {
        return new HoldResponse(
                v.holdId(), v.accountId(),
                new MoneyResponse(v.amountMinor(), v.currency()),
                v.status(), v.expiresAt(), transactionId);
    }
}
