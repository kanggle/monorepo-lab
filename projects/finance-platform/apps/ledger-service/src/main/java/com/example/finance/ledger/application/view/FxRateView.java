package com.example.finance.ledger.application.view;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Per-pair read projection of one cached FX rate quote (25th increment —
 * TASK-FIN-BE-033, ADR-002 read surface). Carries the exact decimal rate as
 * {@link BigDecimal} (application layer never serialises to strings; that is
 * the DTO's responsibility per F5). {@code ageSeconds} and {@code stale} are
 * derived from the staleness boundary used by
 * {@link com.example.finance.ledger.application.ResolveEffectiveFxRate}
 * ({@code now − asOf > staleAfter} → stale; {@code ==} is fresh).
 *
 * @param baseCurrency   ISO-4217 base currency code (e.g. {@code "KRW"})
 * @param foreignCurrency ISO-4217 foreign currency code (e.g. {@code "USD"})
 * @param rate           exact decimal rate (base-minor per foreign-minor)
 * @param asOf           provider-stated rate instant (staleness basis)
 * @param source         provider identifier (audit provenance)
 * @param fetchedAt      when the quote was pulled from the provider
 * @param ageSeconds     {@code now − asOf} in whole seconds (may be negative on clock skew)
 * @param stale          {@code true} when {@code now − asOf > staleAfter}
 */
public record FxRateView(
        String baseCurrency,
        String foreignCurrency,
        BigDecimal rate,
        Instant asOf,
        String source,
        Instant fetchedAt,
        long ageSeconds,
        boolean stale) {
}
