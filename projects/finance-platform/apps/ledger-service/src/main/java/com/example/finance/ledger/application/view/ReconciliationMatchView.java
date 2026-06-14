package com.example.finance.ledger.application.view;

import com.example.finance.ledger.domain.money.Money;
import com.example.finance.ledger.domain.reconciliation.ReconciliationMatch;

/**
 * Read view of one {@link ReconciliationMatch} (reconciliation-api.md § 1) — the
 * statement line's external ref, the matched internal {@code journalEntryId}, the
 * {@link Money} (minor units, F5), and (14th incr — TASK-FIN-BE-021) the
 * {@code crossCurrency} audit flag (true iff a base-currency external line matched a
 * foreign internal line by its carrying base; false for every same-currency match).
 */
public record ReconciliationMatchView(String statementLineExternalRef,
                                      String journalEntryId, Money money,
                                      boolean crossCurrency) {

    public static ReconciliationMatchView from(ReconciliationMatch m) {
        return new ReconciliationMatchView(m.externalRef(), m.journalEntryId(), m.money(),
                m.crossCurrency());
    }
}
