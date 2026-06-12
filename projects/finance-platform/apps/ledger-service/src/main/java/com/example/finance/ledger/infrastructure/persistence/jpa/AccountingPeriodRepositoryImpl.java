package com.example.finance.ledger.infrastructure.persistence.jpa;

import com.example.finance.ledger.domain.money.Currency;
import com.example.finance.ledger.domain.money.Money;
import com.example.finance.ledger.domain.period.AccountingPeriod;
import com.example.finance.ledger.domain.period.PeriodAccountTotal;
import com.example.finance.ledger.domain.period.PeriodBalanceSnapshot;
import com.example.finance.ledger.domain.period.PeriodStatus;
import com.example.finance.ledger.domain.period.repository.AccountingPeriodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** JPA adapter for {@link AccountingPeriodRepository}. */
@Component
@RequiredArgsConstructor
public class AccountingPeriodRepositoryImpl implements AccountingPeriodRepository {

    private final AccountingPeriodJpaRepository periodJpa;
    private final PeriodBalanceSnapshotJpaRepository snapshotJpa;

    @Override
    public AccountingPeriod save(AccountingPeriod period) {
        return periodJpa.save(period);
    }

    @Override
    public Optional<AccountingPeriod> findById(String periodId, String tenantId) {
        return periodJpa.findByPeriodIdAndTenantId(periodId, tenantId);
    }

    @Override
    public List<AccountingPeriod> findAll(String tenantId) {
        return periodJpa.findByTenantIdOrderByFromDesc(tenantId);
    }

    @Override
    public List<AccountingPeriod> findOverlapping(String tenantId, Instant from, Instant to) {
        return periodJpa.findOverlapping(tenantId, from, to);
    }

    @Override
    public Optional<AccountingPeriod> findCovering(String tenantId, Instant postedAt,
                                                   PeriodStatus status) {
        return periodJpa.findCovering(tenantId, postedAt, status).stream().findFirst();
    }

    @Override
    public void saveSnapshot(String periodId, String tenantId, PeriodBalanceSnapshot snapshot) {
        List<PeriodBalanceSnapshotJpaEntity> rows = snapshot.accounts().stream()
                .map(a -> PeriodBalanceSnapshotJpaEntity.of(
                        periodId, tenantId, a.ledgerAccountCode(),
                        a.debitTotal().minorUnits(), a.creditTotal().minorUnits(),
                        a.debitTotal().currency()))
                .toList();
        snapshotJpa.saveAll(rows);
    }

    @Override
    public Optional<PeriodBalanceSnapshot> findSnapshot(String periodId, String tenantId) {
        List<PeriodBalanceSnapshotJpaEntity> rows =
                snapshotJpa.findByPeriodIdAndTenantIdOrderByLedgerAccountCode(periodId, tenantId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Currency currency = rows.get(0).currency();
        List<PeriodAccountTotal> accounts = rows.stream()
                .map(r -> new PeriodAccountTotal(
                        r.ledgerAccountCode(),
                        Money.of(r.debitMinor(), r.currency()),
                        Money.of(r.creditMinor(), r.currency())))
                .toList();
        return Optional.of(PeriodBalanceSnapshot.of(accounts, currency));
    }
}
