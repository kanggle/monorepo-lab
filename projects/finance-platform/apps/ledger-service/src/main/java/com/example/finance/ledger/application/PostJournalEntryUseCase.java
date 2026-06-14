package com.example.finance.ledger.application;

import com.example.finance.ledger.application.port.outbound.ClockPort;
import com.example.finance.ledger.application.port.outbound.LedgerEventPublisher;
import com.example.finance.ledger.domain.account.LedgerAccount;
import com.example.finance.ledger.domain.account.LedgerAccountCodes;
import com.example.finance.ledger.domain.account.repository.LedgerAccountRepository;
import com.example.finance.ledger.domain.audit.AuditLog;
import com.example.finance.ledger.domain.audit.AuditLogRepository;
import com.example.finance.ledger.domain.error.LedgerErrors.LedgerPeriodClosedException;
import com.example.finance.ledger.domain.journal.JournalEntry;
import com.example.finance.ledger.domain.journal.JournalLine;
import com.example.finance.ledger.domain.journal.repository.JournalRepository;
import com.example.finance.ledger.domain.period.PeriodStatus;
import com.example.finance.ledger.domain.period.repository.AccountingPeriodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * The single guarded write path (architecture.md § boundary rules). Persists a
 * balanced {@link JournalEntry} together with its lines and an append-only audit
 * row in ONE transaction. The entry's balance is already self-validated by its
 * factory, so by the time it reaches here the books cannot go out of balance.
 *
 * <p>Per-customer wallet accounts are created lazily on first posting (the chart
 * of accounts seeds only the two platform GL accounts). This is the ONLY
 * {@code @Transactional} write boundary; the consumer and any future controller
 * funnel through it.
 */
@Service
@RequiredArgsConstructor
public class PostJournalEntryUseCase {

    private static final String ACTOR = "finance-ledger-service";
    private static final String AGGREGATE_TYPE = "JournalEntry";

    private final JournalRepository journalRepository;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final AuditLogRepository auditLogRepository;
    private final AccountingPeriodRepository accountingPeriodRepository;
    private final LedgerEventPublisher ledgerEventPublisher;
    private final RecordFxAcquisitionLots recordFxAcquisitionLots;
    private final ClockPort clock;

    /**
     * Persist a posted entry + lazily-create any referenced wallet account +
     * append the audit row, all in the caller's transaction. The caller
     * ({@link PostFromTransactionUseCase}) opens the {@code @Transactional}
     * boundary that also inserts the dedupe row.
     */
    @Transactional
    public JournalEntry post(JournalEntry entry, String reason) {
        // The auto-journal (consumer) path records the service as the audit actor.
        // Delegates to the actor overload with the default — byte-identical behaviour
        // (net-zero; the manual-posting path passes the operator subject instead).
        return post(entry, reason, ACTOR);
    }

    /**
     * Persist a posted entry with an explicit audit {@code actor} (5th increment —
     * the manual-posting path records the operator subject; the no-actor overload
     * above delegates here with the {@code finance-ledger-service} default). Same
     * guarded write path otherwise — closed-period guard, lazy wallet creation, the
     * audit row, and the {@code entry.posted} outbox append, all in this Tx.
     */
    @Transactional
    public JournalEntry post(JournalEntry entry, String reason, String actor) {
        Instant now = clock.now();
        guardClosedPeriod(entry);
        for (JournalLine line : entry.lines()) {
            ensureAccountExists(line.ledgerAccountCode(), entry.tenantId(), now);
        }
        JournalEntry saved = journalRepository.save(entry);
        // (16th incr — TASK-FIN-BE-024, ADR-001 D2) Materialize an FX acquisition
        // lot per position-increasing foreign line, in THIS transaction — atomic
        // with the entry. Shadow / write-only: lots are created here (and backfilled
        // by V10) but nothing consumes them yet (FIN-BE-025). The lines' IDENTITY
        // ids exist now (post-save), so each lot's seq is the line id. A KRW line,
        // a zero-amount revaluation line, or a position-reducing foreign line
        // produces no lot (the 3-clause predicate in RecordFxAcquisitionLots).
        recordFxAcquisitionLots.record(saved);
        auditLogRepository.save(AuditLog.of(
                entry.tenantId(), AGGREGATE_TYPE, entry.entryId(), "POSTED",
                actor, auditSummary(entry), reason, now));
        // (3rd incr) Append the GL/AP feed row in THIS transaction — atomic with
        // the entry + audit (transactional outbox; the feed can never diverge from
        // the books). A guard-rejected posting threw above → no row appended.
        ledgerEventPublisher.publishEntryPosted(saved);
        return saved;
    }

    /**
     * Posting guard (architecture.md § Accounting Period § Posting guard). If a
     * CLOSED period covers the entry's {@code postedAt} the books are locked →
     * {@link LedgerPeriodClosedException} (→ DLT on the consumer path; the dedupe
     * row is not written). <b>Net-zero</b>: {@code findCovering} empty — the common
     * case, and always when no period is defined — posting proceeds byte-identically
     * to the first increment (periods are optional, absence = unrestricted).
     */
    private void guardClosedPeriod(JournalEntry entry) {
        if (accountingPeriodRepository.findCovering(
                entry.tenantId(), entry.postedAt(), PeriodStatus.CLOSED).isPresent()) {
            throw new LedgerPeriodClosedException(
                    "journal posting into a CLOSED accounting period (postedAt="
                            + entry.postedAt() + ", entryId=" + entry.entryId() + ")");
        }
    }

    private void ensureAccountExists(String code, String tenantId, Instant now) {
        if (!ledgerAccountRepository.existsByCode(code, tenantId)) {
            ledgerAccountRepository.save(LedgerAccount.of(
                    code, tenantId, LedgerAccountCodes.typeForCode(code), now));
        }
    }

    private static String auditSummary(JournalEntry entry) {
        // (8th incr) Use the base-currency totals so a multi-currency entry's audit
        // row does not throw on cross-currency arithmetic (debitTotal()/creditTotal()
        // are single-currency only). The base totals balance by construction.
        return "entryId=" + entry.entryId()
                + " lines=" + entry.lines().size()
                + " baseDebitTotal=" + entry.baseDebitTotal().toMinorString()
                + " baseCreditTotal=" + entry.baseCreditTotal().toMinorString()
                + " baseCurrency=" + entry.baseCurrency().code()
                + (entry.isReversal() ? " reversalOf=" + entry.reversalOfEntryId() : "");
    }
}
