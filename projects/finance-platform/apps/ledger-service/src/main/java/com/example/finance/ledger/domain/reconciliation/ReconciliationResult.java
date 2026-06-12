package com.example.finance.ledger.domain.reconciliation;

import java.util.List;

/**
 * The output of one {@link ReconciliationMatcher} run (architecture.md
 * § Reconciliation): the recorded {@link ReconciliationMatch}es and the OPEN
 * {@link ReconciliationDiscrepancy}s. The matched {@link ExternalStatementLine}s
 * have had their {@code matchStatus} flipped to {@code MATCHED} in place. The
 * ingest use case persists both lists in its transaction. Pure value carrier.
 */
public record ReconciliationResult(List<ReconciliationMatch> matches,
                                   List<ReconciliationDiscrepancy> discrepancies) {

    public int matchedCount() {
        return matches.size();
    }

    public int discrepancyCount() {
        return discrepancies.size();
    }
}
