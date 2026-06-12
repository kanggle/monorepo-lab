package com.example.finance.ledger.presentation.dto;

import com.example.finance.ledger.application.view.AccountLineView;

import java.time.Instant;

/** One element of GET /accounts/{code}/entries (ledger-api.md § 2). */
public record AccountLineResponse(String entryId, Instant postedAt,
                                  String direction, MoneyResponse money) {

    public static AccountLineResponse from(AccountLineView v) {
        return new AccountLineResponse(v.entryId(), v.postedAt(),
                v.direction().name(), MoneyResponse.from(v.money()));
    }
}
