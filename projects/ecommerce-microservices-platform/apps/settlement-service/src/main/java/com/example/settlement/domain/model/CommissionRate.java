package com.example.settlement.domain.model;

/**
 * A commission rate in <b>basis points</b> (bps; {@code 1000 bps = 10%}). An exact
 * integer in {@code [0, 10000]} (0% … 100%) — never a float / {@code BigDecimal}
 * (the only arithmetic the domain performs is {@code gross × bps / 10000}, an exact
 * integer division rounded HALF_UP, see {@link CommissionPolicy}).
 *
 * <p>{@code source} records whether the effective rate came from a per-seller
 * override or fell back to the platform default — surfaced on the rate-read API.
 */
public record CommissionRate(int rateBps, Source source) {

    /** Lower / upper bound of a valid rate, inclusive. */
    public static final int MIN_BPS = 0;
    public static final int MAX_BPS = 10_000;

    public enum Source {
        /** An operator-set per-seller rate row exists. */
        SELLER_OVERRIDE,
        /** No per-seller row — the platform default applies. */
        PLATFORM_DEFAULT
    }

    public CommissionRate {
        if (rateBps < MIN_BPS || rateBps > MAX_BPS) {
            throw new InvalidCommissionRateException(rateBps);
        }
    }

    public static CommissionRate sellerOverride(int rateBps) {
        return new CommissionRate(rateBps, Source.SELLER_OVERRIDE);
    }

    public static CommissionRate platformDefault(int rateBps) {
        return new CommissionRate(rateBps, Source.PLATFORM_DEFAULT);
    }

    /** {@code true} when the rate is in range — used to validate before constructing. */
    public static boolean isValidBps(int rateBps) {
        return rateBps >= MIN_BPS && rateBps <= MAX_BPS;
    }
}
