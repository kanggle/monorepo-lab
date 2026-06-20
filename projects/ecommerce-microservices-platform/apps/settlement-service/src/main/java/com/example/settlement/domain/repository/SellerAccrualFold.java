package com.example.settlement.domain.repository;

/**
 * The per-seller fold of the in-window {@code commission_accrual} rows computed at
 * period close (architecture.md § Close-time aggregation). Read-only over the
 * immutable accrual ledger (NEVER mutated — F3):
 * <pre>
 *   payableNetMinor  = Σ seller_net_minor (ACCRUAL positive − REVERSAL negative)
 *   commissionMinor  = Σ commission_minor
 *   accrualCount     = number of accrual rows folded
 * </pre>
 * A seller whose {@link #payableNetMinor} is {@code ≤ 0} after the fold is skipped
 * (no {@code seller_payout} row — net-zero skip, decision 7).
 */
public record SellerAccrualFold(
        String sellerId,
        long payableNetMinor,
        long commissionMinor,
        int accrualCount
) {
}
