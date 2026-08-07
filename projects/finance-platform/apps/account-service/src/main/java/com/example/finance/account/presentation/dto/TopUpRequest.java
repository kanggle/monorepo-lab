package com.example.finance.account.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Wire form of an operator top-up (account-api.md § POST /{id}/topups). */
public record TopUpRequest(
        @NotNull(message = "money is required")
        @Valid
        MoneyDto money,

        @Size(max = 256, message = "reason must be ≤ 256 chars")
        String reason) {
}
