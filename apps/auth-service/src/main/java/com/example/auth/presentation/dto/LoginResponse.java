package com.example.auth.presentation.dto;

public record LoginResponse(
    String accessToken,
    String refreshToken,
    long expiresIn
) {}
