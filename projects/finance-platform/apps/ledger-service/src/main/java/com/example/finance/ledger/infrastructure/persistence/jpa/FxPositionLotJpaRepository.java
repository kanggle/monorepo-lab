package com.example.finance.ledger.infrastructure.persistence.jpa;

import com.example.finance.ledger.domain.journal.FxPositionLot;
import com.example.finance.ledger.domain.money.Currency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data repository for FX acquisition lots (PK = lot_id). The open-lots
 * finder is a derived query ordered FIFO by {@code (acquired_at, seq)} — the
 * consumption order FIN-BE-025 will walk.
 */
public interface FxPositionLotJpaRepository
        extends JpaRepository<FxPositionLot, String> {

    List<FxPositionLot>
            findByTenantIdAndLedgerAccountCodeAndCurrencyAndRemainingForeignMinorGreaterThanOrderByAcquiredAtAscSeqAsc(
            String tenantId, String ledgerAccountCode, Currency currency, long remainingForeignMinor);
}
