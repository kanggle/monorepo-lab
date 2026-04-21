package com.example.user.presentation.dto.response;

import com.example.user.application.result.UserProfileResult;

import java.time.Instant;
import java.util.UUID;

public record UserProfileResponse(
        UUID userId,
        String email,
        String name,
        String nickname,
        String phone,
        String profileImageUrl,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    public static UserProfileResponse from(UserProfileResult result) {
        return new UserProfileResponse(
                result.userId(),
                result.email(),
                result.name(),
                result.nickname(),
                result.phone(),
                result.profileImageUrl(),
                result.status(),
                result.createdAt(),
                result.updatedAt()
        );
    }
}
