package com.example.finance.ledger.application;

import com.example.finance.ledger.application.view.FxPositionLotsView;
import com.example.finance.ledger.domain.journal.FxPositionLot;
import com.example.finance.ledger.domain.journal.repository.FxPositionLotRepository;
import com.example.finance.ledger.domain.money.Currency;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read the tenant's open FX acquisition lots for one {@code (ledgerAccountCode,
 * currency)} position (20th increment — TASK-FIN-BE-028, architecture.md § FX
 * position lots). Mirrors {@link GetFxCostFlowConfigUseCase} — pure read,
 * {@code @Transactional(readOnly=true)}, tenant-scoped by the caller's
 * {@code tenantId}.
 *
 * <p>Delegates to {@link FxPositionLotRepository#findOpenLots} (lots with
 * {@code remaining_foreign_minor > 0}, ordered {@code (acquired_at, seq)} ASC —
 * the FIFO walk order). An empty position returns an empty list with zero summary
 * (AC-3: not a 404). The controller resolves the currency string before calling
 * this use case; the resolved {@link Currency} is passed in directly.
 */
@Service
@RequiredArgsConstructor
public class GetFxPositionLotsUseCase {

    private final FxPositionLotRepository fxPositionLotRepository;

    @Transactional(readOnly = true)
    public FxPositionLotsView get(String tenantId, String ledgerAccountCode, Currency currency) {
        List<FxPositionLot> openLots =
                fxPositionLotRepository.findOpenLots(tenantId, ledgerAccountCode, currency);
        return FxPositionLotsView.from(openLots);
    }
}
