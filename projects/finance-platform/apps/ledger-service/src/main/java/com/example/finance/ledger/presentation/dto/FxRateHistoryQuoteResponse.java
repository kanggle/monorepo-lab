package com.example.finance.ledger.presentation.dto;

import com.example.finance.ledger.application.view.FxRateHistoryView;

import java.time.Instant;

/**
 * Per-row JSON response for one entry in the FX rate history drill (27th increment —
 * TASK-FIN-BE-040). {@code rate} is serialised as a <b>string</b> (exact decimal, F5 wire
 * form — consistent with {@link FxRateResponse} and {@code settlementRate}/{@code closingRate}).
 * No staleness fields — history is raw provenance, not a live-cache freshness check.
 *
 * @param rate      exact decimal rate as a string (F5 — NOT a float)
 * @param asOf      provider-stated rate instant (ISO-8601)
 * @param fetchedAt when the quote was pulled from the provider (ISO-8601)
 * @param source    provider identifier (audit provenance)
 */
public record FxRateHistoryQuoteResponse(
        String rate,
        Instant asOf,
        Instant fetchedAt,
        String source) {

    /** Factory from the application-layer view. {@code rate} is wired as a plain-string decimal (F5). */
    public static FxRateHistoryQuoteResponse from(FxRateHistoryView view) {
        return new FxRateHistoryQuoteResponse(
                view.rate().toPlainString(),
                view.asOf(),
                view.fetchedAt(),
                view.source());
    }
}
