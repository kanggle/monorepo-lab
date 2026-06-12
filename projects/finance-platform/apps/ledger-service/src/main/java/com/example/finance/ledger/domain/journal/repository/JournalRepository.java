package com.example.finance.ledger.domain.journal.repository;

import com.example.finance.ledger.domain.journal.JournalEntry;
import com.example.finance.ledger.domain.journal.JournalLine;
import com.example.finance.ledger.domain.money.Currency;

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

    /**
     * The entry for a source event id (manual idempotent-replay return, 5th
     * increment). The manual path namespaces the client {@code Idempotency-Key} as
     * {@code manual:{key}} into {@code source.sourceEventId}; a replay returns the
     * original entry instead of re-posting (F1).
     */
    Optional<JournalEntry> findBySourceEventId(String sourceEventId, String tenantId);

    /** A page of lines posted to one ledger account, most-recent entry first. */
    LinePage findLinesByAccountCode(String ledgerAccountCode, String tenantId,
                                    int page, int size);

    /** Debit/credit totals per ledger account (trial balance), tenant-scoped. */
    List<AccountTotals> accountTotals(String tenantId);

    /** Debit/credit totals for one ledger account, tenant-scoped. */
    Optional<AccountTotals> accountTotals(String ledgerAccountCode, String tenantId);

    /**
     * Debit/credit totals for one {@code (ledgerAccountCode, currency)} position —
     * the one foreign position's transaction balance plus its base-currency (KRW)
     * carrying (9th increment, FX revaluation, TASK-FIN-BE-015). Empty when the
     * account has no lines in that currency. Implemented by filtering the existing
     * per-{@code (account, currency)} totals to the requested currency (no new JPQL,
     * no migration).
     */
    Optional<AccountTotals> accountTotalsForCurrency(String ledgerAccountCode,
                                                     Currency currency, String tenantId);

    /**
     * Per-account debit/credit totals over entries with {@code postedAt < to}
     * (tenant-scoped) — the close-time period snapshot (architecture.md
     * § Accounting Period § Close-time snapshot). Same shape as
     * {@link #accountTotals(String)} bounded by the period's exclusive upper edge.
     */
    List<AccountTotals> accountTotalsUpTo(String tenantId, java.time.Instant to);

    /** Count of journal entries with {@code postedAt < to}, tenant-scoped (period entryCount). */
    long countEntriesUpTo(String tenantId, java.time.Instant to);

    /** A line + the id/postedAt of its owning entry (for the per-account view). */
    record LineRow(String entryId, java.time.Instant postedAt, JournalLine line) {
    }

    record LinePage(List<LineRow> content, int page, int size,
                    long totalElements, int totalPages) {
    }

    /**
     * Per-account debit/credit totals (minor units) for a single transaction
     * currency, plus the base-currency (KRW) consolidated sums (8th increment — the
     * base sums are KRW regardless of {@code currency}, so summing them across rows
     * yields the base-currency grand total).
     */
    record AccountTotals(String ledgerAccountCode, String currency,
                         long debitMinor, long creditMinor,
                         long baseDebitMinor, long baseCreditMinor) {
    }
}
