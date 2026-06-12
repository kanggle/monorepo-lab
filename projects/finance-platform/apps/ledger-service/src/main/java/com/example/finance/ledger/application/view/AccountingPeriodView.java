package com.example.finance.ledger.application.view;

import com.example.finance.ledger.domain.period.AccountingPeriod;
import com.example.finance.ledger.domain.period.PeriodBalanceSnapshot;
import com.example.finance.ledger.domain.period.PeriodStatus;

import java.time.Instant;

/**
 * Read view of an accounting period (ledger-api.md § 5–8). The {@code snapshot}
 * is present only for a CLOSED period (the close-time trial-balance record);
 * {@code null} while OPEN.
 */
public record AccountingPeriodView(
        String periodId,
        PeriodStatus status,
        Instant from,
        Instant to,
        Instant closedAt,
        String closedBy,
        Long entryCount,
        PeriodBalanceSnapshot snapshot) {

    /** A list/summary view (no snapshot). */
    public static AccountingPeriodView summary(AccountingPeriod p) {
        return new AccountingPeriodView(p.periodId(), p.status(), p.from(), p.to(),
                p.closedAt(), p.closedBy(), p.entryCount(), null);
    }

    /** A detail view carrying the close-time snapshot (null while OPEN). */
    public static AccountingPeriodView detail(AccountingPeriod p, PeriodBalanceSnapshot snapshot) {
        return new AccountingPeriodView(p.periodId(), p.status(), p.from(), p.to(),
                p.closedAt(), p.closedBy(), p.entryCount(), snapshot);
    }
}
