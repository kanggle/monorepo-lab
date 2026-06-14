package com.example.user.application.event;

import java.time.Instant;
import java.util.UUID;

public record UserWithdrawnSpringEvent(
        UUID userId,
        Instant withdrawnAt,
        String tenantId
) {}
