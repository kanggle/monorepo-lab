package com.example.account.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * TASK-MONO-772 S2 — body of {@code POST /internal/accounts/{accountId}/verified-email:match}
 * (admin-to-account.md). The address the caller's invitation went to; account-service compares it with the
 * account's own email. Validation messages never echo the value (R4).
 */
public record VerifiedEmailMatchRequest(

        @NotBlank(message = "expectedEmail must not be blank")
        @Size(max = 320, message = "expectedEmail must be at most 320 characters")
        String expectedEmail
) {
}
