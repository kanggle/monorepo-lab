package com.example.finance.account.domain.money;

import java.util.Map;
import java.util.Objects;

/**
 * Supported ISO-4217 currency with its minor-unit scale (fintech F5).
 *
 * <p>The minor-unit scale is the number of decimal digits the currency
 * subdivides into — KRW has scale 0 (no sub-unit), USD/EUR scale 2 (cents).
 * {@link Money} is always carried as an integer count of these minor units so
 * no {@code float}/{@code double} ever touches a monetary value.
 *
 * <p>Pure Java — no Spring/JPA. The v1 supported set is intentionally small;
 * an unknown code surfaces as {@code CURRENCY_MISMATCH} per the API contract.
 */
public enum Currency {

    KRW(0),
    USD(2),
    EUR(2),
    JPY(0);

    private static final Map<String, Currency> BY_CODE = Map.of(
            "KRW", KRW,
            "USD", USD,
            "EUR", EUR,
            "JPY", JPY);

    private final int minorUnitScale;

    Currency(int minorUnitScale) {
        this.minorUnitScale = minorUnitScale;
    }

    /** Number of decimal digits in the currency's minor unit (KRW=0, USD=2). */
    public int minorUnitScale() {
        return minorUnitScale;
    }

    public String code() {
        return name();
    }

    /**
     * Resolve a 3-letter ISO-4217 code to a supported currency.
     *
     * @throws UnsupportedCurrencyException if the code is null, not 3 letters,
     *         or not in the v1 supported set (mapped to 422 CURRENCY_MISMATCH).
     */
    public static Currency of(String code) {
        Objects.requireNonNull(code, "currency");
        String upper = code.trim().toUpperCase();
        if (upper.length() != 3) {
            throw new UnsupportedCurrencyException(
                    "currency must be a 3-letter ISO-4217 code: " + code);
        }
        Currency c = BY_CODE.get(upper);
        if (c == null) {
            throw new UnsupportedCurrencyException("unsupported currency: " + code);
        }
        return c;
    }

    /** Thrown for an unknown/unsupported currency code (→ CURRENCY_MISMATCH). */
    public static final class UnsupportedCurrencyException extends RuntimeException {
        public UnsupportedCurrencyException(String message) {
            super(message);
        }
    }
}
