package com.example.finance.ledger.application.port.outbound;

import com.example.finance.ledger.domain.money.Currency;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * Outbound port for fetching the latest external FX market rate for a currency pair (23rd
 * increment — TASK-FIN-BE-031, ADR-002 D1). Config-gated infrastructure adapters
 * ({@code noop} / {@code stub} / {@code http}) implement it; the default {@code noop} adapter
 * returns {@link Optional#empty()} so an unconfigured service makes <b>zero</b> external calls
 * (net-zero).
 *
 * <p>A failed external call, an unsupported pair, or a disabled feed all surface as
 * {@link Optional#empty()} — the port never throws (best-effort; the {@code http} adapter wraps
 * every failure mode in a catch-all). The caller ({@code RefreshFxRateQuotesUseCase}) simply skips
 * an empty result (partial cache load is allowed).
 */
public interface FxRateProviderPort {

    /**
     * The latest known quote for {@code base→foreign}, or empty when unavailable (provider error,
     * unsupported pair, or noop default).
     */
    Optional<RateQuote> latestQuote(Currency base, Currency foreign);

    /**
     * A fetched rate quote. {@code rate} is base-minor-per-foreign-minor — the SAME unit
     * convention as {@code closingRate} / {@code settlementRate} (an EXACT {@link BigDecimal}, not a
     * minor amount). {@code asOf} = the provider-stated rate instant; {@code source} = the provider
     * identifier (audit).
     */
    record RateQuote(BigDecimal rate, Instant asOf, String source) {
    }
}
