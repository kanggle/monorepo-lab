package com.example.settlement.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The marketplace commission-split rule — a pure domain service (no framework, no
 * persistence). Given a line's gross amount and the effective rate, it produces the
 * platform commission and the seller's net proceeds:
 *
 * <pre>
 *   commission_minor = round(gross_minor × rate_bps / 10000)   (HALF_UP, ≥ 0)
 *   seller_net_minor = gross_minor − commission_minor          (remainder, exact)
 * </pre>
 *
 * <p>{@code seller_net} is the remainder so the split always reconciles
 * ({@code commission + seller_net == gross}, no second rounding — F1). The only
 * arithmetic is an exact integer ratio rounded HALF_UP; {@code BigDecimal} is used
 * solely as the HALF_UP rounding vehicle on {@code long × int} (which can exceed
 * {@code long} mid-multiply), never as the stored type.
 *
 * <p><b>net-zero degrade (D8, AC-9):</b> a {@code rateBps} of {@code 0} yields
 * {@code commission = 0}, {@code seller_net = gross} — a single-seller standalone
 * store keeps everything, economically identical to "no marketplace economics".
 */
public final class CommissionPolicy {

    private CommissionPolicy() {
    }

    /**
     * Splits {@code grossMinor} by {@code rateBps}. {@code grossMinor} may be
     * negative (a reversal context passes the original gross; callers typically
     * negate the whole split instead — see {@link CommissionSplit#negated()}).
     */
    public static CommissionSplit split(long grossMinor, int rateBps) {
        if (!CommissionRate.isValidBps(rateBps)) {
            throw new InvalidCommissionRateException(rateBps);
        }
        long commissionMinor = BigDecimal.valueOf(grossMinor)
                .multiply(BigDecimal.valueOf(rateBps))
                .divide(BigDecimal.valueOf(10_000), 0, RoundingMode.HALF_UP)
                .longValueExact();
        long sellerNetMinor = grossMinor - commissionMinor;
        return new CommissionSplit(grossMinor, rateBps, commissionMinor, sellerNetMinor);
    }

    /** Convenience overload taking a {@link CommissionRate}. */
    public static CommissionSplit split(long grossMinor, CommissionRate rate) {
        return split(grossMinor, rate.rateBps());
    }
}
