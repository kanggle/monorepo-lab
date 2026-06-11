package com.example.scmplatform.demandplanning.adapter.inbound.web.dto;

import com.example.scmplatform.demandplanning.application.usecase.ApproveSuggestionUseCase;
import com.example.scmplatform.demandplanning.domain.model.SuggestionStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/**
 * Response for {@code POST /suggestions/{id}/approve} (demand-planning-api.md):
 * the materialized suggestion + the linked procurement DRAFT PO.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApproveResponse(
        UUID id,
        SuggestionStatus status,
        UUID poId,
        String poStatus
) {
    public static ApproveResponse from(ApproveSuggestionUseCase.ApproveResult r) {
        return new ApproveResponse(r.suggestionId(), r.status(), r.poId(), r.poStatus());
    }
}
