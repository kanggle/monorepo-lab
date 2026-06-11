package com.example.scmplatform.demandplanning.adapter.inbound.web.dto;

import com.example.scmplatform.demandplanning.domain.model.ReorderSuggestion;
import com.example.scmplatform.demandplanning.domain.model.SuggestionSource;
import com.example.scmplatform.demandplanning.domain.model.SuggestionStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for a single reorder suggestion.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SuggestionResponse(
        UUID id,
        String skuCode,
        UUID warehouseId,
        UUID supplierId,
        int suggestedQty,
        SuggestionStatus status,
        SuggestionSource source,
        Integer triggerAvailableQty,
        UUID materializedPoId,
        Instant createdAt
) {
    public static SuggestionResponse from(ReorderSuggestion s) {
        return new SuggestionResponse(
                s.getId(), s.getSkuCode(), s.getWarehouseId(), s.getSupplierId(),
                s.getSuggestedQty(), s.getStatus(), s.getSource(),
                s.getTriggerAvailableQty(), s.getMaterializedPoId(), s.getCreatedAt());
    }
}
