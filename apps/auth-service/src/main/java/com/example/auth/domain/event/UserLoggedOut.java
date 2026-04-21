package com.example.auth.domain.event;

import java.util.UUID;

public record UserLoggedOut(
    UUID userId,
    String sessionId
) {}
