package com.example.finance.ledger.domain.money;

import java.util.Objects;

/**
 * Money value object — an integer count of currency minor units plus the
 * {@link Currency} (fintech F5). Mirrors account-service's {@code Money}.
 *
 * <p><b>F5 invariant</b>: money is represented and computed ONLY as a
 * {@code long} count of minor units. There is no {@code float}/{@code double}
 * anywhere in this type, its callers, or its JSON form (the contract serializes
 * {@code minorUnits} as a string). Rounding error that would create or destroy
 * funds is therefore structurally impossible.
 *
 * <p>Pure Java — no Spring/JPA. Currency-mismatched arithmetic raises
 * {@link CurrencyMismatchException} (mapped to 422 {@code CURRENCY_MISMATCH}).
 * Negative amounts are rejected at construction.
 */
public final class Money {

    private final long minorUnits;
    private final Currency currency;

    private Money(long minorUnits, Currency currency) {
        this.minorUnits = minorUnits;
        this.currency = currency;
    }

    /** Construct from a non-negative minor-unit count and a resolved currency. */
    public static Money of(long minorUnits, Currency currency) {
        Objects.requireNonNull(currency, "currency");
        if (minorUnits < 0) {
            throw new IllegalArgumentException(
                    "amount must be non-negative minor units: " + minorUnits);
        }
        return new Money(minorUnits, currency);
    }

    /** Construct from a string-encoded integer (the API/JSON wire form). */
    public static Money of(String minorUnits, Currency currency) {
        Objects.requireNonNull(minorUnits, "amount");
        long parsed;
        try {
            parsed = Long.parseLong(minorUnits.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "amount must be an integer minor-unit string: " + minorUnits);
        }
        return of(parsed, currency);
    }

    public static Money zero(Currency currency) {
        return of(0L, currency);
    }

    public long minorUnits() {
        return minorUnits;
    }

    public Currency currency() {
        return currency;
    }

    /** String-encoded integer minor units — the contract JSON form (F5). */
    public String toMinorString() {
        return Long.toString(minorUnits);
    }

    public boolean isZero() {
        return minorUnits == 0L;
    }

    public boolean isPositive() {
        return minorUnits > 0L;
    }

    public Money add(Money other) {
        ensureSameCurrency(other);
        return new Money(Math.addExact(this.minorUnits, other.minorUnits), this.currency);
    }

    public Money subtract(Money other) {
        ensureSameCurrency(other);
        long result = Math.subtractExact(this.minorUnits, other.minorUnits);
        if (result < 0) {
            throw new IllegalArgumentException(
                    "subtraction would yield a negative amount");
        }
        return new Money(result, this.currency);
    }

    /** Absolute difference of two amounts (always non-negative). */
    public Money absoluteDifference(Money other) {
        ensureSameCurrency(other);
        long diff = Math.abs(Math.subtractExact(this.minorUnits, other.minorUnits));
        return new Money(diff, this.currency);
    }

    public boolean isGreaterThan(Money other) {
        ensureSameCurrency(other);
        return this.minorUnits > other.minorUnits;
    }

    public boolean isLessThan(Money other) {
        ensureSameCurrency(other);
        return this.minorUnits < other.minorUnits;
    }

    public boolean isGreaterThanOrEqual(Money other) {
        ensureSameCurrency(other);
        return this.minorUnits >= other.minorUnits;
    }

    private void ensureSameCurrency(Money other) {
        Objects.requireNonNull(other, "other");
        if (this.currency != other.currency) {
            throw new CurrencyMismatchException(
                    "Currency mismatch: " + this.currency + " vs " + other.currency);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money money)) return false;
        return minorUnits == money.minorUnits && currency == money.currency;
    }

    @Override
    public int hashCode() {
        return Objects.hash(minorUnits, currency);
    }

    @Override
    public String toString() {
        return minorUnits + " " + currency;
    }

    /** Mixed-currency arithmetic guard (→ 422 CURRENCY_MISMATCH). */
    public static final class CurrencyMismatchException extends RuntimeException {
        public CurrencyMismatchException(String message) {
            super(message);
        }
    }
}
