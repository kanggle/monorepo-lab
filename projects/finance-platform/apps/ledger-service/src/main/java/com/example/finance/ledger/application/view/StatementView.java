package com.example.finance.ledger.application.view;

import com.example.finance.ledger.domain.reconciliation.ExternalStatement;
import com.example.finance.ledger.domain.reconciliation.ReconciliationDiscrepancy;
import com.example.finance.ledger.domain.reconciliation.ReconciliationMatch;
import com.example.finance.ledger.domain.reconciliation.StatementSource;

import java.time.LocalDate;
import java.util.List;

/**
 * Read view of an ingested {@link ExternalStatement} plus its matches +
 * discrepancies (reconciliation-api.md § 1 / § 3 — the ingest response and the
 * statement detail). {@code matchedCount} / {@code discrepancyCount} are the list
 * sizes (the emitted summary). Discrepancies are recorded OPEN and never
 * auto-closed (F8).
 */
public record StatementView(
        String statementId,
        String ledgerAccountCode,
        StatementSource source,
        LocalDate statementDate,
        int matchedCount,
        int discrepancyCount,
        List<ReconciliationMatchView> matches,
        List<DiscrepancyView> discrepancies) {

    public static StatementView of(ExternalStatement statement,
                                   List<ReconciliationMatch> matches,
                                   List<ReconciliationDiscrepancy> discrepancies) {
        List<ReconciliationMatchView> matchViews = matches.stream()
                .map(ReconciliationMatchView::from).toList();
        List<DiscrepancyView> discrepancyViews = discrepancies.stream()
                .map(DiscrepancyView::from).toList();
        return new StatementView(
                statement.statementId(), statement.ledgerAccountCode(), statement.source(),
                statement.statementDate(), matchViews.size(), discrepancyViews.size(),
                matchViews, discrepancyViews);
    }
}
