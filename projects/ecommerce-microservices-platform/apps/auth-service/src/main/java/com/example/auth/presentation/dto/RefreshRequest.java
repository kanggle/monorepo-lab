package com.example.auth.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshRequest(
    @NotBlank(message = "refreshToken is required")
    @Size(min = 10, max = 512, message = "refreshToken must be between 10 and 512 characters")
    String refreshToken
) {}
