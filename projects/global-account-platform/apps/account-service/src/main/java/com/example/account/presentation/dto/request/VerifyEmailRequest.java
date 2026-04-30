package com.example.account.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/accounts/signup/verify-email} (TASK-BE-114).
 */
public record VerifyEmailRequest(
        @NotBlank(message = "token is required")
        String token
) {
}
