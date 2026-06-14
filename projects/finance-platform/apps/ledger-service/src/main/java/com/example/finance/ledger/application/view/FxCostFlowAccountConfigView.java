package com.example.finance.ledger.application.view;

import com.example.finance.ledger.domain.journal.CostFlowMethod;
import com.example.finance.ledger.domain.journal.FxCostFlowAccountConfig;

import java.time.Instant;

/**
 * Read projection of a per-account FX cost-flow method override (21st increment —
 * TASK-FIN-BE-029). Unlike the per-tenant {@code FxCostFlowConfigView}, there is no
 * "weighted-average default" projection — the account list surfaces only the configured override
 * rows (an account with no override row simply does not appear; it inherits the per-tenant default
 * resolved at settlement time).
 *
 * @param ledgerAccountCode the ledger account the override applies to
 * @param method            the override cost-flow method ({@code WEIGHTED_AVERAGE} | {@code FIFO})
 * @param updatedBy         the operator who last set it
 * @param updatedAt         when it was last set
 */
public record FxCostFlowAccountConfigView(String ledgerAccountCode, CostFlowMethod method,
                                          String updatedBy, Instant updatedAt) {

    /** Project a persisted account override row. */
    public static FxCostFlowAccountConfigView from(FxCostFlowAccountConfig config) {
        return new FxCostFlowAccountConfigView(
                config.ledgerAccountCode(), config.method(),
                config.updatedBy(), config.updatedAt());
    }
}
