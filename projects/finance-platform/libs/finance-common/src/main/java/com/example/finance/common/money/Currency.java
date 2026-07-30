package com.example.finance.common.money;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

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
 *
 * <p>Shared by {@code account-service} and {@code ledger-service}
 * (ADR-003 Option A, TASK-FIN-BE-064). The supported set below is a
 * <b>finance-platform product decision</b>, which is precisely why this type
 * lives in a project-scoped module and not in repo-root {@code libs/}
 * (ADR-003 § 1.2).
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
     * @throws UnsupportedCurrencyException if the code is not 3 letters, or not
     *         in the v1 supported set (mapped to 422 CURRENCY_MISMATCH).
     * @throws NullPointerException if the code is {@code null}
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

    /**
     * Resolve a 3-letter ISO-4217 code, mapping an {@link UnsupportedCurrencyException}
     * to a caller-chosen exception so each call site keeps its own contract error type
     * (e.g. {@code VALIDATION_ERROR} instead of the default {@code CURRENCY_MISMATCH}).
     * The factory receives the offending code. A {@code null} code still throws
     * {@link NullPointerException} via {@link #of(String)} — unchanged (not remapped).
     *
     * @param onUnsupported builds the exception to throw for an unknown/unsupported code
     */
    public static Currency ofOrThrow(String code,
                                     Function<String, ? extends RuntimeException> onUnsupported) {
        try {
            return of(code);
        } catch (UnsupportedCurrencyException e) {
            throw onUnsupported.apply(code);
        }
    }

    /** Thrown for an unknown/unsupported currency code (→ CURRENCY_MISMATCH). */
    public static final class UnsupportedCurrencyException extends RuntimeException {
        public UnsupportedCurrencyException(String message) {
            super(message);
        }
    }
}
