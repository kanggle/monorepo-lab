package com.example.settlement.domain.period;

/**
 * Thrown when {@code POST /api/admin/settlements/periods} would open a second period
 * over a window an OPEN period already covers exactly. Surfaces as HTTP 409
 * {@code PERIOD_ALREADY_OPEN} (TASK-BE-535).
 *
 * <p>The duplicate is refused because {@code close} folds the in-window accruals into
 * {@code seller_payout} rows — two OPEN periods over one accrual window, each closed,
 * pay each seller twice.
 *
 * <p>Scoped to OPEN periods only (the backing index is partial): re-opening the same
 * window after the earlier period was CLOSED is a legitimate correction re-run and
 * stays allowed. Genuine <em>overlap</em> between non-identical windows also stays
 * allowed — see {@link SettlementPeriod}.
 */
public class PeriodAlreadyOpenException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PeriodAlreadyOpenException(String message, Throwable cause) {
        super(message, cause);
    }
}
