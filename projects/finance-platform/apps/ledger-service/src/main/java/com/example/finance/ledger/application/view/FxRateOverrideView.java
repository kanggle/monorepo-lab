package com.example.finance.ledger.application.view;

import com.example.finance.ledger.domain.journal.FxRateOverride;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Read projection of a tenant's FX contract-rate override (28th increment —
 * TASK-FIN-BE-042). When the tenant has no override row for the pair, {@link #none(String, String)}
 * returns an "absent" view ({@code present=false}, {@code rate}/audit fields {@code null}) so the
 * GET can surface "no contract rate set for this pair" (resolution falls through to the feed).
 *
 * @param baseCurrency    ISO-4217 base currency code (always {@code "KRW"} in v1)
 * @param foreignCurrency ISO-4217 foreign currency code (e.g. {@code "USD"})
 * @param present         {@code true} when a contract override row exists for the pair
 * @param rate            the exact contract rate ({@code null} when absent)
 * @param updatedBy       the operator who last set it ({@code null} when absent)
 * @param updatedAt       when it was last set ({@code null} when absent)
 */
public record FxRateOverrideView(String baseCurrency, String foreignCurrency, boolean present,
                                 BigDecimal rate, String updatedBy, Instant updatedAt) {

    /** The "no override set" view for a pair — resolution falls through to the feed. */
    public static FxRateOverrideView none(String baseCurrency, String foreignCurrency) {
        return new FxRateOverrideView(baseCurrency, foreignCurrency, false, null, null, null);
    }

    /** Project a persisted override row. */
    public static FxRateOverrideView from(FxRateOverride override) {
        return new FxRateOverrideView(
                override.baseCurrency(), override.foreignCurrency(), true,
                override.rate(), override.updatedBy(), override.updatedAt());
    }
}
