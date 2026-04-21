package com.example.auth.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LogoutRequest(
    @NotBlank(message = "refreshToken is required")
    @Size(max = 512, message = "refreshToken is too long")
    String refreshToken
) {}
