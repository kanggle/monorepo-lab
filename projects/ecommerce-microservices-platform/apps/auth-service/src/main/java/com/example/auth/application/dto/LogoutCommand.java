package com.example.auth.application.dto;

import java.util.UUID;

public record LogoutCommand(
    String refreshToken,
    String accessToken,
    UUID userId,
    String email,
    String ipAddress,
    String userAgent
) {}
