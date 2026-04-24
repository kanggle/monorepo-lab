package com.example.auth.domain.event;

import java.util.UUID;

public record SessionLimitExceeded(
    UUID userId,
    String evictedSessionId,
    String newSessionId
) {}
