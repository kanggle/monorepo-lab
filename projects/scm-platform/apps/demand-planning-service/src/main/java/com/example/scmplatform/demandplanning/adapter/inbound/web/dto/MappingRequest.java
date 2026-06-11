package com.example.scmplatform.demandplanning.adapter.inbound.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record MappingRequest(
        @NotNull UUID supplierId,
        @NotNull @Min(1) Integer defaultOrderQty,
        @NotNull @Min(0) Integer leadTimeDays,
        @NotBlank @Size(min = 3, max = 3) String currency
) {
}
