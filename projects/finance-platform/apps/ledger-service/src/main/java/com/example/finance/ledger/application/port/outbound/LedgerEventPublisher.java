package com.example.finance.ledger.application.port.outbound;

import com.example.finance.ledger.domain.journal.JournalEntry;
import com.example.finance.ledger.domain.period.AccountingPeriod;
import com.example.finance.ledger.domain.reconciliation.ExternalStatement;
import com.example.finance.ledger.domain.reconciliation.ReconciliationDiscrepancy;

/**
 * Append-side outbound port for the GL/AP feed (3rd increment, TASK-FIN-BE-009 —
 * architecture.md § Event publication). Called <b>inside</b> the domain write
 * {@code @Transactional} (the posting / close use case) so the outbox row commits
 * atomically with the entry+audit / close+snapshot — the feed can never diverge
 * from the books (F1/T3).
 *
 * <p>The implementation builds the canonical envelope (the same shape
 * ledger-service's own consumer parses) and persists a {@code ledger_outbox} row;
 * the relay ({@code LedgerOutboxPublisher}) forwards it to Kafka asynchronously.
 * The domain / application layers stay unaware of Kafka — this is the only seam.
 */
public interface LedgerEventPublisher {

    /**
     * Append a {@code finance.ledger.entry.posted} outbox row for a posted entry
     * (auto-journal or reversal). Payload: {@code entryId, postedAt, balanced
     * lines, source, reversalOfEntryId?}.
     */
    void publishEntryPosted(JournalEntry entry);

    /**
     * Append a {@code finance.ledger.period.closed} outbox row when a period
     * closes. Payload: {@code periodId, from, to, closedAt, entryCount} — all
     * carried on the {@link AccountingPeriod} aggregate after close.
     */
    void publishPeriodClosed(AccountingPeriod period);

    /**
     * Append a {@code finance.ledger.reconciliation.completed} outbox row when a
     * statement is ingested + matched (4th increment, TASK-FIN-BE-010). Payload:
     * {@code statementId, ledgerAccountCode, source, statementDate, matchedCount,
     * discrepancyCount}.
     */
    void publishReconciliationCompleted(ExternalStatement statement,
                                        int matchedCount, int discrepancyCount);

    /**
     * Append a {@code finance.ledger.reconciliation.discrepancy.detected} outbox
     * row — one per recorded discrepancy, in the same ingest transaction (F8 — the
     * discrepancy is emitted but never auto-resolved). Payload: {@code
     * discrepancyId, ledgerAccountCode, type, expectedMinor, actualMinor, currency,
     * externalRef?, journalEntryId?}.
     */
    void publishDiscrepancyDetected(ReconciliationDiscrepancy discrepancy);
}
