package com.example.scmplatform.procurement.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
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
