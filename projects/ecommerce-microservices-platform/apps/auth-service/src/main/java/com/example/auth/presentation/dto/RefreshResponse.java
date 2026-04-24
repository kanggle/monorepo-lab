package com.example.auth.presentation.dto;

public record RefreshResponse(
    String accessToken,
    String refreshToken,
    long expiresIn
) {}
