package com.example.finance.ledger.infrastructure.persistence.jpa;

import com.example.finance.ledger.domain.journal.FxPositionLot;
import com.example.finance.ledger.domain.journal.repository.FxPositionLotRepository;
import com.example.finance.ledger.domain.money.Currency;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** JPA adapter for {@link FxPositionLotRepository}. */
@Component
@RequiredArgsConstructor
public class FxPositionLotRepositoryImpl implements FxPositionLotRepository {

    private final FxPositionLotJpaRepository jpa;

    @Override
    public FxPositionLot save(FxPositionLot lot) {
        return jpa.save(lot);
    }

    @Override
    public List<FxPositionLot> findOpenLots(String tenantId, String ledgerAccountCode,
                                            Currency currency) {
        return jpa
                .findByTenantIdAndLedgerAccountCodeAndCurrencyAndRemainingForeignMinorGreaterThanOrderByAcquiredAtAscSeqAsc(
                        tenantId, ledgerAccountCode, currency, 0L);
    }
}
