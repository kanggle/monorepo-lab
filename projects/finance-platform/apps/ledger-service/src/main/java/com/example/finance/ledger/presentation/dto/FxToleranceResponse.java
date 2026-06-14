package com.example.finance.ledger.presentation.dto;

import com.example.finance.ledger.application.view.FxToleranceView;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Response for the FX reconciliation tolerance endpoints (13th increment —
 * TASK-FIN-BE-020). The EXACT default surfaces {@code { toleranceBps: 0,
 * floorMinor: 0 }} with the audit fields omitted (null → not serialised) when the
 * tenant has no configured row.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FxToleranceResponse(int toleranceBps, long floorMinor,
                                  String updatedBy, Instant updatedAt) {

    public static FxToleranceResponse from(FxToleranceView view) {
        return new FxToleranceResponse(view.toleranceBps(), view.floorMinor(),
                view.updatedBy(), view.updatedAt());
    }
}
