package com.example.auth.domain.event;

public record LoginFailed(
    String email,
    String ipAddress,
    String reason
) {}
