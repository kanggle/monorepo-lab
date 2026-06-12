package com.example.finance.ledger.application.view;

import com.example.finance.ledger.domain.journal.EntryDirection;
import com.example.finance.ledger.domain.money.Money;

import java.math.BigDecimal;

/**
 * A single journal line for the read API (architecture.md § REST endpoints).
 * (8th incr) carries its transaction {@code money} plus the {@code exchangeRate}
 * (provenance) and the {@code baseAmount} (its value in the base/KRW currency).
 */
public record JournalLineView(String ledgerAccountCode, EntryDirection direction, Money money,
                              BigDecimal exchangeRate, Money baseAmount) {
}
