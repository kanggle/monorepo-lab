package com.example.finance.ledger.presentation.dto;

import com.example.finance.ledger.application.view.FxRatesView;

import java.util.List;

/**
 * Response for {@code GET /api/finance/ledger/fx-rates} (25th increment —
 * TASK-FIN-BE-033). Wraps the ordered list of cached FX rate quotes plus a top-level
 * {@code feedEnabled} flag that signals whether the operator's omitted-rate fallback
 * is active (AC-4 — mirrors
 * {@link com.example.finance.ledger.application.port.outbound.FxRateFeedSettings#feedEnabled()}).
 *
 * <p>An empty cache (feed never polled, or recently disabled) returns an empty
 * {@code rates} array with the current {@code feedEnabled} value (AC-1 — 200, not 404).
 *
 * @param feedEnabled {@code true} when the FX rate feed is enabled
 * @param rates       all cached currency-pair quotes, sorted {@code (baseCurrency,
 *                    foreignCurrency)} ASC (deterministic); may be empty
 */
public record FxRatesResponse(
        boolean feedEnabled,
        List<FxRateResponse> rates) {

    /** Factory from the application-layer view. Maps each {@link FxRateResponse} with string rate. */
    public static FxRatesResponse from(FxRatesView view) {
        List<FxRateResponse> rateResponses = view.rates().stream()
                .map(FxRateResponse::from)
                .toList();
        return new FxRatesResponse(view.feedEnabled(), rateResponses);
    }
}
