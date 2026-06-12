package com.example.finance.ledger.application;

import com.example.finance.ledger.application.port.outbound.ClockPort;
import com.example.finance.ledger.domain.audit.AuditLog;
import com.example.finance.ledger.domain.audit.AuditLogRepository;
import com.example.finance.ledger.domain.error.LedgerErrors.AccountingPeriodOverlapException;
import com.example.finance.ledger.domain.period.AccountingPeriod;
import com.example.finance.ledger.domain.period.repository.AccountingPeriodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Opens an accounting period (architecture.md § Accounting Period). One
 * {@code @Transactional} boundary: build an OPEN period (the factory validates
 * {@code from < to} → {@code ACCOUNTING_PERIOD_INVALID_WINDOW}); reject a window
 * overlapping any existing period for the tenant ({@code ACCOUNTING_PERIOD_OVERLAP},
 * the non-overlap invariant); persist + append the audit row.
 */
@Service
@RequiredArgsConstructor
public class OpenAccountingPeriodUseCase {

    private static final String AGGREGATE_TYPE = "AccountingPeriod";

    private final AccountingPeriodRepository periodRepository;
    private final AuditLogRepository auditLogRepository;
    private final ClockPort clock;

    @Transactional
    public AccountingPeriod open(String tenantId, Instant from, Instant to, String actor) {
        AccountingPeriod period = AccountingPeriod.open(
                UUID.randomUUID().toString(), tenantId, from, to);

        List<AccountingPeriod> overlapping =
                periodRepository.findOverlapping(tenantId, from, to);
        if (!overlapping.isEmpty()) {
            throw new AccountingPeriodOverlapException(
                    "accounting period window [" + from + ", " + to + ") overlaps "
                            + overlapping.size() + " existing period(s) for tenant " + tenantId);
        }

        AccountingPeriod saved = periodRepository.save(period);
        auditLogRepository.save(AuditLog.of(
                tenantId, AGGREGATE_TYPE, saved.periodId(), "OPENED",
                actor, auditSummary(saved), "open accounting period", clock.now()));
        return saved;
    }

    private static String auditSummary(AccountingPeriod p) {
        return "periodId=" + p.periodId() + " from=" + p.from() + " to=" + p.to()
                + " status=" + p.status();
    }
}
