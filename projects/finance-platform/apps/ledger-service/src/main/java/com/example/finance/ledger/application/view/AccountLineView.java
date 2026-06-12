package com.example.finance.ledger.application.view;

import com.example.finance.ledger.domain.journal.EntryDirection;
import com.example.finance.ledger.domain.money.Money;

import java.time.Instant;

/** One line posted to a ledger account, for the per-account entries view (ledger-api.md § 2). */
public record AccountLineView(String entryId, Instant postedAt,
                              EntryDirection direction, Money money) {
}
