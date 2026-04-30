package com.example.auth.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record OAuthCallbackRequest(
        @NotBlank
        String provider,

        @NotBlank
        String code,

        @NotBlank
        String state,

        String redirectUri
) {
}
