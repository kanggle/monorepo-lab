package com.example.account.presentation.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(max = 100, message = "Display name must not exceed 100 characters")
        String displayName,

        String phoneNumber,

        String birthDate,

        String locale,

        String timezone,

        String preferences
) {
}
