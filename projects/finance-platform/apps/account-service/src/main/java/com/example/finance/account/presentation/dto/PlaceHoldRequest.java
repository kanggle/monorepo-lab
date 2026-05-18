package com.example.finance.account.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PlaceHoldRequest(
        @NotNull(message = "money is required")
        @Valid
        MoneyDto money,

        @Min(value = 1, message = "expiresInSeconds must be ≥ 1")
        @Max(value = 604800, message = "expiresInSeconds must be ≤ 604800")
        Integer expiresInSeconds,

        @Size(max = 256, message = "reason must be ≤ 256 chars")
        String reason) {
}
