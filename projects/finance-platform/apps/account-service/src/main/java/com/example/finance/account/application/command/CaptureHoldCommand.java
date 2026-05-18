package com.example.finance.account.application.command;

import com.example.finance.account.application.ActorContext;

/** Capture a hold (full/partial; remainder auto-released). */
public record CaptureHoldCommand(ActorContext actor,
                                 String accountId,
                                 String holdId,
                                 String amountMinor,
                                 String currency) {
}
