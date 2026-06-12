package com.example.finance.ledger.application;

import com.example.finance.ledger.application.view.AccountingPeriodView;
import com.example.finance.ledger.domain.error.LedgerErrors.AccountingPeriodNotFoundException;
import com.example.finance.ledger.domain.money.Currency;
import com.example.finance.ledger.domain.period.AccountingPeriod;
import com.example.finance.ledger.domain.period.PeriodBalanceSnapshot;
import com.example.finance.ledger.domain.period.repository.AccountingPeriodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read-side accounting-period use case (architecture.md § REST endpoints): list
 * periods (most-recent first, no snapshot) and period detail (incl. the close-time
 * snapshot, present only for a CLOSED period). Reads are tenant-scoped and
 * side-effect free.
 */
@Service
@RequiredArgsConstructor
public class QueryAccountingPeriodUseCase {

    private final AccountingPeriodRepository periodRepository;

    @Transactional(readOnly = true)
    public List<AccountingPeriodView> listPeriods(String tenantId) {
        return periodRepository.findAll(tenantId).stream()
                .map(AccountingPeriodView::summary)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountingPeriodView getPeriod(String periodId, String tenantId) {
        AccountingPeriod period = periodRepository.findById(periodId, tenantId)
                .orElseThrow(() -> new AccountingPeriodNotFoundException(
                        "accounting period not found: " + periodId));
        PeriodBalanceSnapshot snapshot = period.isClosed()
                ? periodRepository.findSnapshot(periodId, tenantId)
                        // a CLOSED period with no in-window entries persisted no rows →
                        // an empty (zero, in-balance) snapshot is its ending record.
                        .orElseGet(() -> PeriodBalanceSnapshot.of(List.of(), Currency.KRW))
                : null;
        return AccountingPeriodView.detail(period, snapshot);
    }
}
