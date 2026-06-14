package com.example.finance.ledger.domain.reconciliation;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Per-tenant base-leg FX reconciliation tolerance (13th increment — TASK-FIN-BE-020,
 * architecture.md § FX reconciliation tolerance). A pure domain value object —
 * NO Spring/JPA — mirroring the {@code FxRevaluationPolicy} / {@code FxSettlementPolicy}
 * style: the application layer <b>resolves</b> the tenant's configured tolerance and
 * passes it into the pure {@link ReconciliationMatcher}; the matcher never reads a
 * repository.
 *
 * <p>When a foreign external statement line matches an internal line on the
 * transaction (foreign) leg and carries a bank-reported base (KRW) value, FIN-BE-017
 * compared that base to the internal carrying base with an <b>exact</b> {@code !=} and
 * raised an {@code AMOUNT_MISMATCH} on any non-zero difference. Banks routinely report
 * the base value at their own FX rate, a few minor units off the ledger's carrying
 * rate; under an exact compare every such settlement becomes an operator-review
 * discrepancy. This tolerance lets an operator declare an acceptable FX-rounding band:
 * a difference <b>within</b> the band matches cleanly (no discrepancy); a difference
 * <b>above</b> it still raises the {@code AMOUNT_MISMATCH} exactly as FIN-BE-017.
 *
 * <p><b>Band.</b> The allowed band is the <b>looser</b> (larger) of two terms:
 * <ul>
 *   <li>a <b>basis-points</b> term — {@code round_half_up(|expected| * toleranceBps / 10000)},
 *       which scales with the carrying-base magnitude (万分율 of the internal carrying
 *       base); and</li>
 *   <li>an <b>absolute floor</b> {@code absoluteFloorMinor} (base/KRW minor units),
 *       which backstops tiny amounts where the bps term rounds to ~0.</li>
 * </ul>
 * A base difference is <b>within</b> tolerance iff
 * {@code |expected - actual| <= max(absoluteFloorMinor, round_half_up(|expected| * bps / 10000))}.
 * The comparison is <b>inclusive</b> ({@code <=}) — a difference exactly at the band
 * edge is within.
 *
 * <p><b>{@link #EXACT} = net-zero.</b> {@code EXACT == (0, 0)} → the band is
 * {@code max(0, round_half_up(|expected| * 0 / 10000)) == max(0, 0) == 0}, so
 * {@link #isWithinTolerance} is true <b>iff</b> {@code expected == actual}. Under
 * {@code EXACT} the matcher is byte-identical to FIN-BE-017 — no configured tolerance
 * (the dominant path) changes nothing.
 *
 * <p><b>Tolerance applies ONLY to the base (KRW) leg.</b> The transaction (foreign)
 * leg stays an exact {@code (amount, currency, direction)} match — the matcher's
 * candidate selection is unchanged. This object is consulted solely for the base-leg
 * difference check.
 *
 * @param toleranceBps       basis points (万分율) of the internal carrying-base
 *                           magnitude; {@code >= 0}
 * @param absoluteFloorMinor an absolute floor in base/KRW minor units; {@code >= 0}
 */
public record FxTolerance(int toleranceBps, long absoluteFloorMinor) {

    /** Basis-points denominator (10000 bps == 100%). */
    private static final BigDecimal BPS_DENOMINATOR = new BigDecimal(10_000);

    /** The net-zero default — no tolerance; behaves as an exact equality compare. */
    public static final FxTolerance EXACT = new FxTolerance(0, 0L);

    /**
     * Compact constructor — both terms must be non-negative (the DB CHECK is the
     * structural backstop; the use case validates upstream → {@code VALIDATION_ERROR}).
     */
    public FxTolerance {
        if (toleranceBps < 0) {
            throw new IllegalArgumentException("toleranceBps must be >= 0: " + toleranceBps);
        }
        if (absoluteFloorMinor < 0) {
            throw new IllegalArgumentException(
                    "absoluteFloorMinor must be >= 0: " + absoluteFloorMinor);
        }
    }

    /**
     * Whether the base difference {@code |expected - actual|} is within this tolerance.
     * The allowed band is the looser of the bps-derived term (half-up rounded) and the
     * absolute floor; the comparison is inclusive ({@code <=}). Under {@link #EXACT}
     * the band is 0, so this is {@code true} iff {@code expected == actual} (net-zero).
     *
     * @param expectedBaseMinor the internal carrying base (KRW minor units)
     * @param actualBaseMinor   the bank-reported external base (KRW minor units)
     * @return {@code true} iff the absolute difference is within the band
     */
    public boolean isWithinTolerance(long expectedBaseMinor, long actualBaseMinor) {
        long difference = Math.abs(expectedBaseMinor - actualBaseMinor);
        return difference <= band(expectedBaseMinor);
    }

    /**
     * The allowed band in base/KRW minor units: {@code max(absoluteFloorMinor,
     * round_half_up(|expected| * toleranceBps / 10000))}. Under {@link #EXACT} both
     * terms are 0 → the band is 0.
     */
    private long band(long expectedBaseMinor) {
        long bpsBand = new BigDecimal(Math.abs(expectedBaseMinor))
                .multiply(new BigDecimal(toleranceBps))
                .divide(BPS_DENOMINATOR, 0, RoundingMode.HALF_UP)
                .longValueExact();
        return Math.max(absoluteFloorMinor, bpsBand);
    }
}
