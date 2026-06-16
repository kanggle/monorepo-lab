package com.example.finance.ledger.presentation.dto;

import com.example.finance.ledger.application.view.FxRateHistorySummaryView;

import java.util.List;

/**
 * Response for {@code GET /api/finance/ledger/fx-rates/{foreignCurrency}/history}
 * (27th increment — TASK-FIN-BE-040). Wraps the per-pair ordered time series.
 *
 * <p>An unknown / never-polled pair returns an empty {@code quotes} array (200, not 404 —
 * mirrors the list EP's empty-200 stance from FIN-BE-033). {@code base} is always
 * {@code "KRW"} in v1 (the fixed reporting currency).
 *
 * @param base    ISO-4217 base currency code (always {@code "KRW"} in v1)
 * @param foreign ISO-4217 foreign currency code (e.g. {@code "USD"})
 * @param quotes  history rows, newest first; may be empty
 */
public record FxRateHistoryResponse(
        String base,
        String foreign,
        List<FxRateHistoryQuoteResponse> quotes) {

    /** Factory from the application-layer view. Maps each row via {@link FxRateHistoryQuoteResponse}. */
    public static FxRateHistoryResponse from(FxRateHistorySummaryView view) {
        List<FxRateHistoryQuoteResponse> rows = view.quotes().stream()
                .map(FxRateHistoryQuoteResponse::from)
                .toList();
        return new FxRateHistoryResponse(view.baseCurrency(), view.foreignCurrency(), rows);
    }
}
