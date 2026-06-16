package com.example.finance.ledger.application.view;

import java.util.List;

/**
 * Aggregated read projection of the FX rate history audit trail for one currency pair
 * (27th increment — TASK-FIN-BE-040, ADR-002 history-read drill). Returned by
 * {@link com.example.finance.ledger.application.GetFxRateHistoryUseCase}.
 *
 * <p>An unknown / never-polled pair returns an empty {@code quotes} list — 200, not 404
 * (mirrors the list EP's empty-200 stance from FIN-BE-033).
 *
 * @param baseCurrency    ISO-4217 base currency code (always {@code "KRW"} in v1)
 * @param foreignCurrency ISO-4217 foreign currency code (e.g. {@code "USD"})
 * @param quotes          history rows, newest first ({@code fetched_at DESC, id DESC}); may be empty
 */
public record FxRateHistorySummaryView(
        String baseCurrency,
        String foreignCurrency,
        List<FxRateHistoryView> quotes) {
}
