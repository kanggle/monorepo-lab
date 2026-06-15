package com.example.finance.ledger.application.view;

import java.util.List;

/**
 * Aggregated read projection of the FX rate quote cache (25th increment —
 * TASK-FIN-BE-033, ADR-002 read surface). Returned by
 * {@link com.example.finance.ledger.application.GetFxRatesUseCase}.
 *
 * <p>Top-level {@code feedEnabled} exposes whether the FX rate feed is active
 * (per {@link com.example.finance.ledger.application.port.outbound.FxRateFeedSettings#feedEnabled()})
 * so an operator can distinguish "no quotes because the feed is disabled" from
 * "no quotes because the feed is ON but hasn't polled yet". Empty cache when the
 * feed has never polled returns an empty {@code rates} list (not 404 — AC-1).
 *
 * @param feedEnabled {@code true} when the FX rate feed is enabled
 *                    (the operator fallback gate is active)
 * @param rates       all cached currency-pair quotes, sorted {@code (baseCurrency,
 *                    foreignCurrency)} ASC (deterministic; may be empty)
 */
public record FxRatesView(
        boolean feedEnabled,
        List<FxRateView> rates) {
}
