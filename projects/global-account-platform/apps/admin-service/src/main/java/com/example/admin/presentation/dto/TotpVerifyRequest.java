package com.example.admin.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for {@code POST /api/admin/auth/2fa/verify}.
 */
public record TotpVerifyRequest(
        @NotBlank
        @Pattern(regexp = "\\d{6}", message = "totpCode must be 6 digits")
        String totpCode
) {}
