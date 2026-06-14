package com.example.finance.ledger.presentation.dto;

import com.example.finance.ledger.application.view.FxPositionLotsView;

import java.util.List;

/**
 * Response for {@code GET /api/finance/ledger/settlements/{ledgerAccountCode}/{currency}/lots}
 * (20th increment — TASK-FIN-BE-028). Wraps the ordered list of open lots plus a
 * summary. Money summary fields are strings (F5 wire form — consistent with
 * per-lot {@link FxPositionLotResponse} and {@link SettlementResponse}).
 *
 * <p>An empty position returns an empty {@code lots} list with zero summary totals
 * (AC-3: not 404).
 *
 * @param lots                       FIFO-ordered open lots ({@code (acquired_at, seq)} ASC)
 * @param totalRemainingForeignMinor Σ remainingForeignMinor across open lots (string integer)
 * @param totalCarryingBaseMinor     Σ carryingBaseMinor across open lots (string integer)
 * @param lotCount                   number of open lots
 */
public record FxPositionLotsResponse(
        List<FxPositionLotResponse> lots,
        String totalRemainingForeignMinor,
        String totalCarryingBaseMinor,
        int lotCount) {

    /** Factory from the application-layer view. */
    public static FxPositionLotsResponse from(FxPositionLotsView view) {
        List<FxPositionLotResponse> lotResponses = view.lots().stream()
                .map(FxPositionLotResponse::from)
                .toList();
        return new FxPositionLotsResponse(
                lotResponses,
                Long.toString(view.totalRemainingForeignMinor()),
                Long.toString(view.totalCarryingBaseMinor()),
                view.lotCount());
    }
}
