package com.example.finance.account.application.command;

import com.example.finance.account.application.ActorContext;

/** Open a new account (PENDING_KYC). */
public record OpenAccountCommand(ActorContext actor,
                                 String ownerRef,
                                 String currency,
                                 String kycLevel) {
}
