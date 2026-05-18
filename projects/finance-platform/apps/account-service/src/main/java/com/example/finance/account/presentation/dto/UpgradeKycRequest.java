package com.example.finance.account.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpgradeKycRequest(
        @NotBlank(message = "toLevel is required")
        @Pattern(regexp = "^(BASIC|FULL)$", message = "toLevel must be BASIC|FULL")
        String toLevel,

        @Size(max = 256, message = "reason must be ≤ 256 chars")
        String reason) {
}
