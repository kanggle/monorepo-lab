package com.example.settlement.domain.model;

/**
 * The result of splitting one order line's gross amount into the platform's
 * commission and the seller's net proceeds (see {@link CommissionPolicy}). All
 * amounts are integer <b>minor units</b> ({@code long}, KRW implicit).
 *
 * <p><b>Invariant (F1):</b> {@code commissionMinor + sellerNetMinor == grossMinor},
 * always — {@code sellerNetMinor} is the remainder after {@code commissionMinor},
 * so there is never a rounding drift. The constructor enforces it.
 */
public record CommissionSplit(
        long grossMinor,
        int rateBps,
        long commissionMinor,
        long sellerNetMinor) {

    public CommissionSplit {
        if (commissionMinor + sellerNetMinor != grossMinor) {
            throw new IllegalStateException(
                    "commission-split invariant violated: " + commissionMinor + " + "
                            + sellerNetMinor + " != " + grossMinor);
        }
    }

    /** The negation of this split — every amount sign-flipped (a REVERSAL of it). */
    public CommissionSplit negated() {
        return new CommissionSplit(-grossMinor, rateBps, -commissionMinor, -sellerNetMinor);
    }
}
