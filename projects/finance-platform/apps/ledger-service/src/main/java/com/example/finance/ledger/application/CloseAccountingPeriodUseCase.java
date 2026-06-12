package com.example.finance.ledger.application;

import com.example.finance.ledger.application.port.outbound.ClockPort;
import com.example.finance.ledger.application.port.outbound.LedgerEventPublisher;
import com.example.finance.ledger.application.view.AccountingPeriodView;
import com.example.finance.ledger.domain.audit.AuditLog;
import com.example.finance.ledger.domain.audit.AuditLogRepository;
import com.example.finance.ledger.domain.error.LedgerErrors.AccountingPeriodNotFoundException;
import com.example.finance.ledger.domain.journal.repository.JournalRepository;
import com.example.finance.ledger.domain.journal.repository.JournalRepository.AccountTotals;
import com.example.finance.ledger.domain.money.Currency;
import com.example.finance.ledger.domain.money.LedgerReportingCurrency;
import com.example.finance.ledger.domain.money.Money;
import com.example.finance.ledger.domain.period.AccountingPeriod;
import com.example.finance.ledger.domain.period.PeriodAccountTotal;
import com.example.finance.ledger.domain.period.PeriodBalanceSnapshot;
import com.example.finance.ledger.domain.period.repository.AccountingPeriodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Closes an accounting period (architecture.md § Accounting Period § Close-time
 * snapshot). One {@code @Transactional} boundary: load the period (else
 * {@code ACCOUNTING_PERIOD_NOT_FOUND}), compute the trial-balance snapshot over
 * entries with {@code postedAt < to} (tenant-scoped, reusing the per-account
 * totals query), flip OPEN→CLOSED with {@code entryCount} (re-close →
 * {@code ACCOUNTING_PERIOD_ALREADY_CLOSED}), persist the immutable snapshot rows +
 * the audit row. The snapshot grand totals are in balance and equal the live trial
 * balance at close. Terminal — no outbox / no emission this increment.
 */
@Service
@RequiredArgsConstructor
public class CloseAccountingPeriodUseCase {

    private static final String AGGREGATE_TYPE = "AccountingPeriod";

    private final AccountingPeriodRepository periodRepository;
    private final JournalRepository journalRepository;
    private final AuditLogRepository auditLogRepository;
    private final LedgerEventPublisher ledgerEventPublisher;
    private final ClockPort clock;

    @Transactional
    public AccountingPeriodView close(String periodId, String tenantId, String actor) {
        AccountingPeriod period = periodRepository.findById(periodId, tenantId)
                .orElseThrow(() -> new AccountingPeriodNotFoundException(
                        "accounting period not found: " + periodId));

        PeriodBalanceSnapshot snapshot = computeSnapshot(tenantId, period.to());
        long entryCount = journalRepository.countEntriesUpTo(tenantId, period.to());

        // close() throws AccountingPeriodAlreadyClosedException if not OPEN.
        period.close(clock.now(), actor, entryCount);

        periodRepository.save(period);
        periodRepository.saveSnapshot(periodId, tenantId, snapshot);
        auditLogRepository.save(AuditLog.of(
                tenantId, AGGREGATE_TYPE, periodId, "CLOSED",
                actor, auditSummary(period, snapshot), "close accounting period", clock.now()));
        // (3rd incr) Append the GL/AP feed row in THIS transaction — atomic with
        // the close + snapshot (transactional outbox). entryCount + closedAt are
        // already stamped on the aggregate by close() above.
        ledgerEventPublisher.publishPeriodClosed(period);

        return AccountingPeriodView.detail(period, snapshot);
    }

    private PeriodBalanceSnapshot computeSnapshot(String tenantId, Instant to) {
        List<AccountTotals> totals = journalRepository.accountTotalsUpTo(tenantId, to);
        // (8th incr) The snapshot consolidates in the base/reporting currency (KRW) so
        // a multi-currency period still closes in balance — the per-account rows carry
        // each account's base-currency (KRW) totals and the grand totals are the
        // base-currency consolidation. In the all-KRW path this is byte-identical to
        // the per-increment behaviour (base == original).
        Currency base = LedgerReportingCurrency.BASE;
        List<PeriodAccountTotal> accounts = new ArrayList<>(totals.size());
        for (AccountTotals t : totals) {
            accounts.add(new PeriodAccountTotal(t.ledgerAccountCode(),
                    Money.of(t.baseDebitMinor(), base), Money.of(t.baseCreditMinor(), base)));
        }
        return PeriodBalanceSnapshot.of(accounts, base);
    }

    private static String auditSummary(AccountingPeriod p, PeriodBalanceSnapshot snapshot) {
        return "periodId=" + p.periodId() + " status=" + p.status()
                + " entryCount=" + p.entryCount()
                + " grandDebit=" + snapshot.grandDebitTotal().toMinorString()
                + " grandCredit=" + snapshot.grandCreditTotal().toMinorString()
                + " inBalance=" + snapshot.inBalance();
    }
}
