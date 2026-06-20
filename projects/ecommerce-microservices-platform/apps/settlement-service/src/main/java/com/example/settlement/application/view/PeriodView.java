package com.example.settlement.application.view;

import com.example.settlement.domain.period.SettlementPeriod;

import java.time.Instant;
import java.util.List;

/**
 * Read view of a {@link SettlementPeriod} (settlement-api.md § period endpoints).
 * {@code payouts} is populated only on the close response (the period's just-created
 * PENDING rows); list/summary views carry an empty list.
 */
public record PeriodView(
        String periodId,
        Instant from,
        Instant to,
        String status,
        Instant closedAt,
        Integer sellerCount,
        List<PayoutView> payouts
) {

    /** Summary (list) view — no payout rows. */
    public static PeriodView summary(SettlementPeriod p) {
        return new PeriodView(p.periodId(), p.from(), p.to(), p.status().name(),
                p.closedAt(), p.sellerCount(), List.of());
    }

    /** Detail (close) view — includes the period's PENDING payout rows. */
    public static PeriodView detail(SettlementPeriod p, List<PayoutView> payouts) {
        return new PeriodView(p.periodId(), p.from(), p.to(), p.status().name(),
                p.closedAt(), p.sellerCount(), payouts);
    }
}
