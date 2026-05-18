package com.example.finance.account.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CaptureHoldRequest(
        @NotNull(message = "money is required")
        @Valid
        MoneyDto money) {
}
