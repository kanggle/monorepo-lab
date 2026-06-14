package com.example.finance.ledger.presentation.dto;

import com.example.finance.ledger.application.view.FxCostFlowAccountConfigView;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Response for the per-account FX cost-flow method override endpoints (21st increment —
 * TASK-FIN-BE-029). One element per configured override; the audit fields are always populated
 * for a stored override (a row only exists once an operator set it), but {@code NON_NULL} is kept
 * for parity with {@code FxCostFlowConfigResponse}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FxCostFlowAccountConfigResponse(String ledgerAccountCode, String method,
                                              String updatedBy, Instant updatedAt) {

    public static FxCostFlowAccountConfigResponse from(FxCostFlowAccountConfigView view) {
        return new FxCostFlowAccountConfigResponse(
                view.ledgerAccountCode(), view.method().name(),
                view.updatedBy(), view.updatedAt());
    }
}
