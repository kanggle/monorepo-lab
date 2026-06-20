package com.example.settlement.presentation.dto;

import com.example.settlement.application.view.PayoutView;

/** One payout row in the period-close response {@code payouts[]} (settlement-api.md). */
public record PayoutResponse(
        String payoutId,
        String sellerId,
        long payableNetMinor,
        long commissionMinor,
        int accrualCount,
        String status,
        String payoutReference,
        String paidAt) {

    public static PayoutResponse from(PayoutView v) {
        return new PayoutResponse(
                v.payoutId(), v.sellerId(), v.payableNetMinor(), v.commissionMinor(),
                v.accrualCount(), v.status(), v.payoutReference(),
                v.paidAt() == null ? null : v.paidAt().toString());
    }
}
