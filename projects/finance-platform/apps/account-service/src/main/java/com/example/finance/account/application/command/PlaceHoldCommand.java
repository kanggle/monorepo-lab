package com.example.finance.account.application.command;

import com.example.finance.account.application.ActorContext;

/** Place a hold (reserve funds; available ≥ amount). */
public record PlaceHoldCommand(ActorContext actor,
                               String accountId,
                               String amountMinor,
                               String currency,
                               Integer expiresInSeconds,
                               String reason) {
}
