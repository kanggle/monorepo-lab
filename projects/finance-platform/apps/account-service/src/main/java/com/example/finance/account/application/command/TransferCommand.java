package com.example.finance.account.application.command;

import com.example.finance.account.application.ActorContext;

/** Atomic transfer to another finance account (hold+capture+credit in one Tx). */
public record TransferCommand(ActorContext actor,
                              String fromAccountId,
                              String toAccountId,
                              String amountMinor,
                              String currency,
                              String reason) {
}
