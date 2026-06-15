package com.example.finance.ledger.presentation.dto;

import com.example.finance.ledger.application.view.FxRateView;

import java.time.Instant;

/**
 * Per-pair JSON response for the FX rate feed read endpoint (25th increment —
 * TASK-FIN-BE-033). {@code rate} is serialised as a <b>string</b> (exact decimal,
 * F5 wire form — {@link java.math.BigDecimal#toPlainString()} to avoid JSON
 * float precision loss and keep the representation consistent with
 * {@code settlementRate} / {@code closingRate} in the settlement / revaluation
 * endpoints). Times are ISO-8601 {@link Instant}; {@code ageSeconds} is a number;
 * {@code stale} is a boolean.
 *
 * <p>{@code ageSeconds} may be negative when {@code asOf} is in the future (clock
 * skew) — the value is not clamped (diagnostic transparency, AC-3 edge case). A
 * negative age implies {@code stale=false} ({@code now − asOf < 0 ≤ staleAfter}).
 *
 * @param baseCurrency    ISO-4217 base currency code (e.g. {@code "KRW"})
 * @param foreignCurrency ISO-4217 foreign currency code (e.g. {@code "USD"})
 * @param rate            exact decimal rate as a string (F5 — NOT a float)
 * @param asOf            provider-stated rate instant (ISO-8601)
 * @param source          provider identifier (audit provenance)
 * @param fetchedAt       when the quote was pulled (ISO-8601)
 * @param ageSeconds      {@code now − asOf} in whole seconds (may be negative)
 * @param stale           {@code true} when {@code now − asOf > staleAfter}
 */
public record FxRateResponse(
        String baseCurrency,
        String foreignCurrency,
        String rate,
        Instant asOf,
        String source,
        Instant fetchedAt,
        long ageSeconds,
        boolean stale) {

    /** Factory from the application-layer view. {@code rate} is wired as a plain-string decimal (F5). */
    public static FxRateResponse from(FxRateView view) {
        return new FxRateResponse(
                view.baseCurrency(),
                view.foreignCurrency(),
                view.rate().toPlainString(),
                view.asOf(),
                view.source(),
                view.fetchedAt(),
                view.ageSeconds(),
                view.stale());
    }
}
