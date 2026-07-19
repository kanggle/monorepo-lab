package com.example.scmplatform.procurement.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for {@code POST /api/procurement/po/from-suggestion}
 * (ADR-MONO-027 D5). Internal entry — caller is {@code demand-planning-service}.
 *
 * <p>No {@code Idempotency-Key} header: idempotency is keyed on
 * {@code sourceSuggestionId} instead. {@code unitPriceRef} is a price
 * reference/placeholder (e.g. {@code "LAST_KNOWN"}), not a number.
 */
public record DraftFromSuggestionRequest(
        @NotBlank @Size(max = 36) String supplierId,
        @NotBlank @Size(min = 3, max = 3) String currency,
        // Provenance marker; only DEMAND_PLANNING is accepted via this entry.
        @NotBlank @Pattern(regexp = "DEMAND_PLANNING") String origin,
        @NotBlank @Size(max = 36) String sourceSuggestionId,
        // wms inbound-expected addressing (ADR-MONO-050 D1/D3/D4). Optional at the
        // wire so older/other callers still work; demand-planning always sends
        // them. destinationWarehouseId = the warehouse that seeded the reorder;
        // destinationNodeType = WMS_WAREHOUSE in v1; leadTimeDays from
        // sku_supplier_map. A PO drafted without them never emits inbound-expected.
        @Size(max = 36) String destinationWarehouseId,
        @Size(max = 30) String destinationNodeType,
        @PositiveOrZero Integer leadTimeDays,
        @NotEmpty @Valid List<Line> lines
) {
    public record Line(
            @Positive int lineNo,
            @NotBlank @Size(max = 100) String sku,
            @Positive int quantity,
            @Size(max = 40) String unitPriceRef
    ) {
    }
}
