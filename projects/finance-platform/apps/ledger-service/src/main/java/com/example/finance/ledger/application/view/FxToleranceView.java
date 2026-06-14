package com.example.finance.ledger.application.view;

import com.example.finance.ledger.domain.reconciliation.FxTolerance;
import com.example.finance.ledger.domain.reconciliation.ReconciliationFxToleranceConfig;

import java.time.Instant;

/**
 * Read projection of a tenant's FX reconciliation tolerance (13th increment —
 * TASK-FIN-BE-020). When the tenant has no configured row, {@link #exact()} returns
 * the EXACT default {@code (0, 0)} with {@code null} audit fields — the GET surfaces
 * the effective tolerance whether or not a row exists.
 *
 * @param toleranceBps the basis-points band term
 * @param floorMinor   the absolute floor (base/KRW minor units)
 * @param updatedBy    the operator who last set it ({@code null} when unset/EXACT default)
 * @param updatedAt    when it was last set ({@code null} when unset/EXACT default)
 */
public record FxToleranceView(int toleranceBps, long floorMinor,
                              String updatedBy, Instant updatedAt) {

    /** The EXACT default surfaced when a tenant has no configured row. */
    public static FxToleranceView exact() {
        return new FxToleranceView(FxTolerance.EXACT.toleranceBps(),
                FxTolerance.EXACT.absoluteFloorMinor(), null, null);
    }

    /** Project a persisted config row. */
    public static FxToleranceView from(ReconciliationFxToleranceConfig config) {
        return new FxToleranceView(config.toleranceBps(), config.floorMinor(),
                config.updatedBy(), config.updatedAt());
    }
}
