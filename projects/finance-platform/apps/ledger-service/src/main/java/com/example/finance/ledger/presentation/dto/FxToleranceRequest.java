package com.example.finance.ledger.presentation.dto;

/**
 * Upsert body for {@code PUT /reconciliation/fx-tolerance} (13th increment —
 * TASK-FIN-BE-020). {@code toleranceBps} = basis points (万分율) of the carrying-base
 * magnitude; {@code floorMinor} = an absolute floor in base/KRW minor units. Both must
 * be {@code >= 0} (the use case enforces → {@code VALIDATION_ERROR}; the DB CHECK is
 * the structural backstop). Boxed so a missing field is null-checked rather than
 * silently defaulted to 0.
 */
public record FxToleranceRequest(Integer toleranceBps, Long floorMinor) {

    /** {@code toleranceBps} or 0 when omitted. */
    public int bpsOrZero() {
        return toleranceBps == null ? 0 : toleranceBps;
    }

    /** {@code floorMinor} or 0 when omitted. */
    public long floorOrZero() {
        return floorMinor == null ? 0L : floorMinor;
    }
}
