package com.example.user.application.result;

import com.example.user.domain.model.UserProfile;

import java.time.Instant;
import java.util.UUID;

public record UserProfileResult(
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
    public static UserProfileResult from(UserProfile profile) {
        return new UserProfileResult(
                profile.getUserId(),
                profile.getEmail().value(),
                profile.getName(),
                profile.getNickname(),
                profile.getPhone(),
                profile.getProfileImageUrl(),
                profile.getStatus().name(),
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }
}
