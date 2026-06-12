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
    private final ClockPort clock;

    /**
     * Persist a posted entry + lazily-create any referenced wallet account +
     * append the audit row, all in the caller's transaction. The caller
     * ({@link PostFromTransactionUseCase}) opens the {@code @Transactional}
     * boundary that also inserts the dedupe row.
     */
    @Transactional
    public JournalEntry post(JournalEntry entry, String reason) {
        Instant now = clock.now();
        guardClosedPeriod(entry);
        for (JournalLine line : entry.lines()) {
            ensureAccountExists(line.ledgerAccountCode(), entry.tenantId(), now);
        }
        JournalEntry saved = journalRepository.save(entry);
        auditLogRepository.save(AuditLog.of(
                entry.tenantId(), AGGREGATE_TYPE, entry.entryId(), "POSTED",
                ACTOR, auditSummary(entry), reason, now));
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
        return "entryId=" + entry.entryId()
                + " lines=" + entry.lines().size()
                + " debitTotal=" + entry.debitTotal().toMinorString()
                + " creditTotal=" + entry.creditTotal().toMinorString()
                + " currency=" + entry.currency().code()
                + (entry.isReversal() ? " reversalOf=" + entry.reversalOfEntryId() : "");
    }
}
