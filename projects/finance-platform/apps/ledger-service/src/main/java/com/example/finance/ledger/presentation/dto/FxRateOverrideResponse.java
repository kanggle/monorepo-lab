package com.example.finance.ledger.presentation.dto;

import com.example.finance.ledger.application.view.FxRateOverrideView;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Response for the FX contract-rate override endpoints (28th increment — TASK-FIN-BE-042).
 * {@code rate} is serialised as a <b>string</b> (exact decimal, F5 wire form — match
 * {@link FxRateHistoryQuoteResponse} / {@link FxRateResponse}). The "absent" view (no contract
 * rate set for the pair) surfaces {@code { baseCurrency, foreignCurrency, present:false }} with the
 * {@code rate}/audit fields omitted (null → not serialised).
 *
 * @param baseCurrency    ISO-4217 base currency code (always {@code "KRW"} in v1)
 * @param foreignCurrency ISO-4217 foreign currency code
 * @param present         {@code true} when a contract override exists for the pair
 * @param rate            exact contract rate as a string (F5 — NOT a float); null when absent
 * @param updatedBy       the operator who last set it; null when absent
 * @param updatedAt       when it was last set (ISO-8601); null when absent
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FxRateOverrideResponse(String baseCurrency, String foreignCurrency, boolean present,
                                     String rate, String updatedBy, Instant updatedAt) {

    public static FxRateOverrideResponse from(FxRateOverrideView view) {
        return new FxRateOverrideResponse(
                view.baseCurrency(),
                view.foreignCurrency(),
                view.present(),
                view.rate() != null ? view.rate().toPlainString() : null,
                view.updatedBy(),
                view.updatedAt());
    }
}
