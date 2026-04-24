package com.example.auth.domain.event;

import java.util.UUID;

public record UserLoggedIn(
    UUID userId,
    String email,
    String ipAddress,
    String userAgent
) {}
