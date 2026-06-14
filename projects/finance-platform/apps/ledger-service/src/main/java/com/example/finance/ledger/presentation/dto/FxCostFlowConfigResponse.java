package com.example.finance.ledger.presentation.dto;

import com.example.finance.ledger.application.view.FxCostFlowConfigView;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Response for the FX cost-flow method config endpoints (15th increment —
 * TASK-FIN-BE-023). The WEIGHTED_AVERAGE default surfaces {@code { method:
 * "WEIGHTED_AVERAGE" }} with the audit fields omitted (null → not serialised) when the
 * tenant has no configured row.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FxCostFlowConfigResponse(String method, String updatedBy, Instant updatedAt) {

    public static FxCostFlowConfigResponse from(FxCostFlowConfigView view) {
        return new FxCostFlowConfigResponse(
                view.method().name(), view.updatedBy(), view.updatedAt());
    }
}
