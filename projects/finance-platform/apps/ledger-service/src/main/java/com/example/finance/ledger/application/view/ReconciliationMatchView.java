package com.example.finance.ledger.application.view;

import com.example.finance.ledger.domain.money.Money;
import com.example.finance.ledger.domain.reconciliation.ReconciliationMatch;

/**
 * Read view of one {@link ReconciliationMatch} (reconciliation-api.md § 1) — the
 * statement line's external ref, the matched internal {@code journalEntryId}, and
 * the {@link Money} (minor units, F5).
 */
public record ReconciliationMatchView(String statementLineExternalRef,
                                      String journalEntryId, Money money) {

    public static ReconciliationMatchView from(ReconciliationMatch m) {
        return new ReconciliationMatchView(m.externalRef(), m.journalEntryId(), m.money());
    }
}
