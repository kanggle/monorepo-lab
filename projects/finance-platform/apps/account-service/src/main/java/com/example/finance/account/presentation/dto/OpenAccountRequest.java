package com.example.finance.account.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OpenAccountRequest(
        @NotBlank(message = "ownerRef is required")
        @Size(max = 64, message = "ownerRef must be ≤ 64 chars")
        String ownerRef,

        @NotBlank(message = "currency is required")
        @Pattern(regexp = "^[A-Za-z]{3}$", message = "currency must be a 3-letter ISO-4217 code")
        String currency,

        @Pattern(regexp = "^(NONE|BASIC|FULL)?$", message = "kycLevel must be NONE|BASIC|FULL")
        String kycLevel) {
}
