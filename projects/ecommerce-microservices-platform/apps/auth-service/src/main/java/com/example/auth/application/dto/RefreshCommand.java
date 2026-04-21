package com.example.auth.application.dto;

public record RefreshCommand(
    String refreshToken,
    String ipAddress,
    String userAgent
) {}
