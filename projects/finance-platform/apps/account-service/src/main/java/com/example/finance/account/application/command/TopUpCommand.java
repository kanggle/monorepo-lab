package com.example.finance.account.application.command;

import com.example.finance.account.application.ActorContext;

/**
 * Operator-initiated internal funding credit — the v1 path by which money enters
 * an account (account-api.md {@code POST /api/finance/accounts/{id}/topups},
 * architecture.md § Balance Model). {@code amountMinor} is the wire form: a
 * string-encoded integer in minor units (F5); {@code currency} must equal the
 * account currency.
 */
public record TopUpCommand(ActorContext actor,
                           String accountId,
                           String amountMinor,
                           String currency,
                           String reason) {
}
