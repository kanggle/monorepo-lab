package com.example.scmplatform.demandplanning.adapter.inbound.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PolicyRequest(
        @NotNull @Min(0) Integer reorderPoint,
        @NotNull @Min(0) Integer safetyStock,
        @NotNull @Min(1) Integer reorderQty
) {
}
