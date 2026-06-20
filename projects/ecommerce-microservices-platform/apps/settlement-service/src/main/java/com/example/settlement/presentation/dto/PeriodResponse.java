package com.example.settlement.presentation.dto;

import com.example.settlement.application.view.PeriodView;

import java.util.List;

/**
 * {@code POST /periods} (201) / {@code POST /periods/{id}/close} (200) response
 * (settlement-api.md). {@code payouts} is present on the close response (the period's
 * just-created PENDING rows) and empty on the open response.
 */
public record PeriodResponse(
        String periodId,
        String from,
        String to,
        String status,
        String closedAt,
        Integer sellerCount,
        List<PayoutResponse> payouts) {

    /** Open response — no payouts, {@code closedAt}/{@code sellerCount} null. */
    public static PeriodResponse summary(PeriodView v) {
        return new PeriodResponse(
                v.periodId(),
                v.from() == null ? null : v.from().toString(),
                v.to() == null ? null : v.to().toString(),
                v.status(),
                v.closedAt() == null ? null : v.closedAt().toString(),
                v.sellerCount(),
                List.of());
    }

    /** Close response — includes the PENDING payout rows. */
    public static PeriodResponse detail(PeriodView v) {
        return new PeriodResponse(
                v.periodId(),
                v.from() == null ? null : v.from().toString(),
                v.to() == null ? null : v.to().toString(),
                v.status(),
                v.closedAt() == null ? null : v.closedAt().toString(),
                v.sellerCount(),
                v.payouts().stream().map(PayoutResponse::from).toList());
    }
}
