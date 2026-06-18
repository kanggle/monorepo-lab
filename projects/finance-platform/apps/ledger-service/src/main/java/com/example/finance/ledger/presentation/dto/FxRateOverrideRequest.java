package com.example.finance.ledger.presentation.dto;

import com.example.finance.ledger.domain.error.LedgerErrors.FxRateOverrideInvalidException;

import java.math.BigDecimal;

/**
 * Upsert body for {@code PUT /settlements/fx-rate-override/{foreignCurrency}} (28th increment —
 * TASK-FIN-BE-042). {@code rate} is the contract rate as an exact-decimal <b>string</b> (F5 wire
 * form — never a JSON float; consistent with {@code settlementRate} / {@code closingRate} and the
 * fx-rate read responses). The use case enforces strictly-positive (→ {@code VALIDATION_ERROR}
 * 400); the DB CHECK is the structural backstop.
 *
 * @param rate the contract rate as a plain-decimal string (e.g. {@code "1325.50000000"})
 */
public record FxRateOverrideRequest(String rate) {

    /**
     * Parse the wire {@code rate} string to an exact {@link BigDecimal}. A null / blank / non-numeric
     * value maps to {@code VALIDATION_ERROR} (400) — never a {@code NumberFormatException} 500. The
     * strictly-positive guard lives in the use case (the DB CHECK is the backstop).
     */
    public BigDecimal parsedRate() {
        if (rate == null || rate.isBlank()) {
            throw new FxRateOverrideInvalidException("contract rate is required");
        }
        try {
            return new BigDecimal(rate.trim());
        } catch (NumberFormatException e) {
            throw new FxRateOverrideInvalidException("contract rate is not a valid decimal: " + rate);
        }
    }
}
