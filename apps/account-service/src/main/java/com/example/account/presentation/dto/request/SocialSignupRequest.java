package com.example.account.presentation.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SocialSignupRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,

        @NotBlank(message = "Provider is required")
        String provider,

        @NotBlank(message = "Provider user ID is required")
        String providerUserId,

        @Size(max = 100, message = "Display name must not exceed 100 characters")
        String displayName
) {
}
