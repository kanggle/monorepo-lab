package com.example.finance.ledger.presentation.dto;

import com.example.finance.ledger.application.view.AccountingPeriodView;

import java.time.Instant;

/**
 * Accounting-period summary response (ledger-api.md § 5 / § 7) — no snapshot.
 * {@code closedAt}/{@code closedBy}/{@code entryCount} are null while OPEN.
 */
public record PeriodResponse(
        String periodId,
        String status,
        Instant from,
        Instant to,
        Instant closedAt,
        String closedBy,
        Long entryCount) {

    public static PeriodResponse from(AccountingPeriodView v) {
        return new PeriodResponse(v.periodId(), v.status().name(), v.from(), v.to(),
                v.closedAt(), v.closedBy(), v.entryCount());
    }
}
