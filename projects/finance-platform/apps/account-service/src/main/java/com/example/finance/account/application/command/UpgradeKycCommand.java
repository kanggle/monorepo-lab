package com.example.finance.account.application.command;

import com.example.finance.account.application.ActorContext;

/** Operator raises KYC level; may transition PENDING_KYC → ACTIVE. */
public record UpgradeKycCommand(ActorContext actor,
                                String accountId,
                                String toLevel,
                                String reason) {
}
