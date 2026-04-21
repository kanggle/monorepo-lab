package com.example.user.presentation.dto.response;

import com.example.user.application.result.UserProfileResult;

import java.time.Instant;
import java.util.UUID;

public record UpdateProfileResponse(
        UUID userId,
        String email,
        String name,
        String nickname,
        String phone,
        String profileImageUrl,
        String status,
        Instant updatedAt
) {
    public static UpdateProfileResponse from(UserProfileResult result) {
        return new UpdateProfileResponse(
                result.userId(),
                result.email(),
                result.name(),
                result.nickname(),
                result.phone(),
                result.profileImageUrl(),
                result.status(),
                result.updatedAt()
        );
    }
}
