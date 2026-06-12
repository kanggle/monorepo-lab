package com.example.finance.ledger.infrastructure.persistence.jpa;

import com.example.finance.ledger.domain.journal.JournalLine;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Spring Data repository for journal lines (per-account view + trial-balance totals). */
public interface JournalLineJpaRepository extends JpaRepository<JournalLine, Long> {

    Page<JournalLine> findByLedgerAccountCodeAndTenantIdOrderByPostedAtDescIdDesc(
            String ledgerAccountCode, String tenantId, Pageable pageable);

    /** Per-account debit/credit totals (minor units) grouped by account + currency. */
    @Query("""
            select new com.example.finance.ledger.infrastructure.persistence.jpa.AccountTotalsRow(
                l.ledgerAccountCode,
                l.currency,
                coalesce(sum(case when l.direction = com.example.finance.ledger.domain.journal.EntryDirection.DEBIT
                    then l.amountMinor else 0 end), 0),
                coalesce(sum(case when l.direction = com.example.finance.ledger.domain.journal.EntryDirection.CREDIT
                    then l.amountMinor else 0 end), 0))
            from JournalLine l
            where l.tenantId = :tenantId
            group by l.ledgerAccountCode, l.currency
            order by l.ledgerAccountCode
            """)
    List<AccountTotalsRow> accountTotals(@Param("tenantId") String tenantId);

    @Query("""
            select new com.example.finance.ledger.infrastructure.persistence.jpa.AccountTotalsRow(
                l.ledgerAccountCode,
                l.currency,
                coalesce(sum(case when l.direction = com.example.finance.ledger.domain.journal.EntryDirection.DEBIT
                    then l.amountMinor else 0 end), 0),
                coalesce(sum(case when l.direction = com.example.finance.ledger.domain.journal.EntryDirection.CREDIT
                    then l.amountMinor else 0 end), 0))
            from JournalLine l
            where l.tenantId = :tenantId and l.ledgerAccountCode = :code
            group by l.ledgerAccountCode, l.currency
            """)
    List<AccountTotalsRow> accountTotalsForCode(@Param("code") String code,
                                                @Param("tenantId") String tenantId);

    /**
     * Per-account totals over lines with {@code postedAt < :to} — the close-time
     * period snapshot. Same as {@link #accountTotals} bounded by the period's
     * exclusive upper edge (half-open {@code [from, to)}).
     */
    @Query("""
            select new com.example.finance.ledger.infrastructure.persistence.jpa.AccountTotalsRow(
                l.ledgerAccountCode,
                l.currency,
                coalesce(sum(case when l.direction = com.example.finance.ledger.domain.journal.EntryDirection.DEBIT
                    then l.amountMinor else 0 end), 0),
                coalesce(sum(case when l.direction = com.example.finance.ledger.domain.journal.EntryDirection.CREDIT
                    then l.amountMinor else 0 end), 0))
            from JournalLine l
            where l.tenantId = :tenantId and l.postedAt < :to
            group by l.ledgerAccountCode, l.currency
            order by l.ledgerAccountCode
            """)
    List<AccountTotalsRow> accountTotalsUpTo(@Param("tenantId") String tenantId,
                                             @Param("to") java.time.Instant to);

    /**
     * Journal lines on one ledger account (tenant-scoped) whose owning entry is
     * NOT already linked by a {@code ReconciliationMatch} — the candidate internal
     * lines for matching (the anti-join against prior matches). Ordered by the
     * line's posting instant + id so the matcher's "first deterministic candidate"
     * is stable across runs.
     */
    @Query("""
            select l from JournalLine l
            where l.tenantId = :tenantId
              and l.ledgerAccountCode = :code
              and l.entryId not in (
                  select m.journalEntryId from ReconciliationMatch m
                  where m.tenantId = :tenantId)
            order by l.postedAt asc, l.id asc
            """)
    List<JournalLine> findUnmatchedInternalLines(@Param("tenantId") String tenantId,
                                                 @Param("code") String code);
}
