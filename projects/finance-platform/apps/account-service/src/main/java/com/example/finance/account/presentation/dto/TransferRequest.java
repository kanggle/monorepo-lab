package com.example.finance.account.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TransferRequest(
        @NotBlank(message = "toAccountId is required")
        String toAccountId,

        @NotNull(message = "money is required")
        @Valid
        MoneyDto money,

        @Size(max = 256, message = "reason must be ≤ 256 chars")
        String reason) {
}
