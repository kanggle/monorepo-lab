package com.example.finance.ledger.application.view;

import com.example.finance.ledger.domain.journal.EntryDirection;
import com.example.finance.ledger.domain.money.Money;

/** A single journal line for the read API (architecture.md § REST endpoints). */
public record JournalLineView(String ledgerAccountCode, EntryDirection direction, Money money) {
}
