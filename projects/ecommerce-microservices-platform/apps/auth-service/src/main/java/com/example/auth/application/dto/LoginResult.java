package com.example.auth.application.dto;

public record LoginResult(
    String accessToken,
    String refreshToken,
    long expiresIn
) {}
