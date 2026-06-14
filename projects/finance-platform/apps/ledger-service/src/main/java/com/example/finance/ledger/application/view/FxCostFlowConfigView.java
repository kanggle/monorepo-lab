package com.example.finance.ledger.application.view;

import com.example.finance.ledger.domain.journal.CostFlowMethod;
import com.example.finance.ledger.domain.journal.FxCostFlowConfig;

import java.time.Instant;

/**
 * Read projection of a tenant's FX cost-flow method config (15th increment —
 * TASK-FIN-BE-023). When the tenant has no configured row, {@link #weightedAverageDefault()}
 * returns the {@code WEIGHTED_AVERAGE} default with {@code null} audit fields — the GET
 * always surfaces the effective method whether or not a row exists.
 *
 * @param method    the effective cost-flow method ({@code WEIGHTED_AVERAGE} when unset)
 * @param updatedBy the operator who last set it ({@code null} when unset/default)
 * @param updatedAt when it was last set ({@code null} when unset/default)
 */
public record FxCostFlowConfigView(CostFlowMethod method,
                                   String updatedBy, Instant updatedAt) {

    /** The WEIGHTED_AVERAGE default surfaced when a tenant has no configured row. */
    public static FxCostFlowConfigView weightedAverageDefault() {
        return new FxCostFlowConfigView(CostFlowMethod.WEIGHTED_AVERAGE, null, null);
    }

    /** Project a persisted config row. */
    public static FxCostFlowConfigView from(FxCostFlowConfig config) {
        return new FxCostFlowConfigView(config.method(), config.updatedBy(), config.updatedAt());
    }
}
