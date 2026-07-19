package com.example.scmplatform.demandplanning.adapter.inbound.web.dto;

import com.example.scmplatform.demandplanning.domain.model.ReorderSuggestion;
import com.example.scmplatform.demandplanning.domain.model.SuggestionSource;
import com.example.scmplatform.demandplanning.domain.model.SuggestionStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for a single reorder suggestion.
 *
 * <p>ADR-MONO-050 D9: {@code warehouseId} is the internal dedup-key UUID; the new
 * {@code warehouseCode} (nullable — BATCH suggestions carry none) is the business
 * code that addresses the PO destination. {@code supplierId} is a supplier code.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SuggestionResponse(
        UUID id,
        String skuCode,
        UUID warehouseId,
        String warehouseCode,
        String supplierId,
        int suggestedQty,
        SuggestionStatus status,
        SuggestionSource source,
        Integer triggerAvailableQty,
        UUID materializedPoId,
        Instant createdAt
) {
    public static SuggestionResponse from(ReorderSuggestion s) {
        return new SuggestionResponse(
                s.getId(), s.getSkuCode(), s.getWarehouseId(), s.getWarehouseCode(), s.getSupplierId(),
                s.getSuggestedQty(), s.getStatus(), s.getSource(),
                s.getTriggerAvailableQty(), s.getMaterializedPoId(), s.getCreatedAt());
    }
}
