package com.example.finance.ledger.application.view;

import com.example.finance.ledger.domain.journal.FxPositionLot;
import com.example.finance.ledger.domain.money.Currency;

import java.util.List;

/**
 * Aggregated read projection of the open FX position lots for one
 * {@code (tenant, ledgerAccountCode, currency)} (20th increment —
 * TASK-FIN-BE-028, architecture.md § FX position lots). Returned by
 * {@link com.example.finance.ledger.application.GetFxPositionLotsUseCase}.
 *
 * <p>Summary fields are the arithmetic sums over the open lot list:
 * <ul>
 *   <li>{@code totalRemainingForeignMinor} = Σ {@code remainingForeignMinor}</li>
 *   <li>{@code totalCarryingBaseMinor} = Σ {@code carryingBaseMinor} — equals the
 *       aggregate position carrying when the D4 invariant holds.</li>
 * </ul>
 *
 * @param lots                       ordered FIFO list of open lots (may be empty)
 * @param totalRemainingForeignMinor Σ remaining foreign minor units across open lots
 * @param totalCarryingBaseMinor     Σ carrying base (KRW) minor units across open lots
 * @param lotCount                   number of open lots ({@code lots.size()})
 */
public record FxPositionLotsView(
        List<FxPositionLotView> lots,
        long totalRemainingForeignMinor,
        long totalCarryingBaseMinor,
        int lotCount) {

    /**
     * Project a list of open lot entities (already ordered FIFO by the repository)
     * into the view, computing the summary totals inline. An empty list produces
     * zero-totals (AC-3: empty position → 200 with zero summary, not 404).
     */
    public static FxPositionLotsView from(List<FxPositionLot> openLots) {
        List<FxPositionLotView> views = openLots.stream()
                .map(FxPositionLotView::from)
                .toList();
        long sumForeign = openLots.stream()
                .mapToLong(FxPositionLot::remainingForeignMinor)
                .sum();
        long sumCarrying = openLots.stream()
                .mapToLong(FxPositionLot::carryingBaseMinor)
                .sum();
        return new FxPositionLotsView(views, sumForeign, sumCarrying, views.size());
    }
}
