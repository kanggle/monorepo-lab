package com.example.finance.account.application.command;

import com.example.finance.account.application.ActorContext;

/** Release a hold (funds back to available). */
public record ReleaseHoldCommand(ActorContext actor,
                                 String accountId,
                                 String holdId) {
}
