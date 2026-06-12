package com.example.finance.ledger.domain.journal.repository;

import com.example.finance.ledger.domain.journal.JournalEntry;
import com.example.finance.ledger.domain.journal.JournalLine;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for journal persistence (architecture.md § Layer Structure).
 * Entries are insert-only (immutable, F3); reads are tenant-scoped. Implemented
 * by an infrastructure JPA adapter.
 */
public interface JournalRepository {

    JournalEntry save(JournalEntry entry);

    Optional<JournalEntry> findByEntryId(String entryId, String tenantId);

    /** The original entry for a source transaction id (reversal lookup, F3). */
    Optional<JournalEntry> findBySourceTransactionId(String sourceTransactionId, String tenantId);

    /** A page of lines posted to one ledger account, most-recent entry first. */
    LinePage findLinesByAccountCode(String ledgerAccountCode, String tenantId,
                                    int page, int size);

    /** Debit/credit totals per ledger account (trial balance), tenant-scoped. */
    List<AccountTotals> accountTotals(String tenantId);

    /** Debit/credit totals for one ledger account, tenant-scoped. */
    Optional<AccountTotals> accountTotals(String ledgerAccountCode, String tenantId);

    /** A line + the id/postedAt of its owning entry (for the per-account view). */
    record LineRow(String entryId, java.time.Instant postedAt, JournalLine line) {
    }

    record LinePage(List<LineRow> content, int page, int size,
                    long totalElements, int totalPages) {
    }

    /** Per-account debit/credit totals (minor units) for a single currency. */
    record AccountTotals(String ledgerAccountCode, String currency,
                         long debitMinor, long creditMinor) {
    }
}
