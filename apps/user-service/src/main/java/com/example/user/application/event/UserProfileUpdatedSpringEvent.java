package com.example.user.application.event;

import java.time.Instant;
import java.util.UUID;

public record UserProfileUpdatedSpringEvent(
        UUID userId,
        String nickname,
        String phone,
        String profileImageUrl,
        Instant updatedAt
) {}
