package com.example.finance.ledger.presentation.dto;

/**
 * Response for {@code POST /api/finance/ledger/fx-rates/refresh}
 * (28th increment — TASK-MONO-300, ADR-002 manual-refresh realized).
 *
 * <p>{@code feedEnabled} mirrors
 * {@link com.example.finance.ledger.application.port.outbound.FxRateFeedSettings#feedEnabled()} —
 * the same flag the GET list endpoint exposes. When {@code false} the feed is disabled (the
 * default / standalone mode); the refresh is a safe no-op and {@code refreshed} will be 0.
 *
 * <p>{@code refreshed} is the number of currency-pair quotes successfully upserted by
 * {@link com.example.finance.ledger.application.RefreshFxRateQuotesUseCase#refresh()} in
 * this call. A partial run (some pairs failed) returns the count that succeeded; a feed-disabled
 * run returns 0. The use case is best-effort / never-throw — a provider failure per pair is
 * logged and skipped, so this value may be less than the number of configured pairs.
 *
 * @param feedEnabled {@code true} when the FX rate feed is enabled
 * @param refreshed   number of pairs successfully upserted in this refresh run
 */
public record FxRatesRefreshResponse(boolean feedEnabled, int refreshed) {

    /** Factory for a successful (possibly partial) refresh run. */
    public static FxRatesRefreshResponse of(boolean feedEnabled, int refreshed) {
        return new FxRatesRefreshResponse(feedEnabled, refreshed);
    }
}
