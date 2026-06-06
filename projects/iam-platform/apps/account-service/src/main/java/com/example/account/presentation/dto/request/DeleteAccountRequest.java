package com.example.account.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;

public record DeleteAccountRequest(
        @NotBlank(message = "Password is required for re-authentication")
        String password,

        String reason
) {
}
