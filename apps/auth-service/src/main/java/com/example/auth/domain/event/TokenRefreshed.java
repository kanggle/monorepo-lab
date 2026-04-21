package com.example.auth.domain.event;

import java.util.UUID;

public record TokenRefreshed(
    UUID userId,
    String sessionId
) {}
