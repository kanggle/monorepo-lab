package com.example.settlement.application.view;

import com.example.settlement.domain.payout.SellerPayout;

import java.time.Instant;

/**
 * Read view of a {@link SellerPayout} row (settlement-api.md § period close response
 * {@code payouts[]}). At close every row is PENDING with a {@code null} reference /
 * paidAt; execution (BE-416) sets those.
 */
public record PayoutView(
        String payoutId,
        String sellerId,
        long payableNetMinor,
        long commissionMinor,
        int accrualCount,
        String status,
        String payoutReference,
        Instant paidAt
) {

    public static PayoutView from(SellerPayout p) {
        return new PayoutView(
                p.payoutId(), p.sellerId(), p.payableNetMinor(), p.commissionMinor(),
                p.accrualCount(), p.status().name(), p.payoutReference(), p.paidAt());
    }
}
