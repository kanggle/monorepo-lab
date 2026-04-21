package com.example.auth.application.dto;

public record RefreshResult(
    String accessToken,
    String refreshToken,
    long expiresIn
) {}
