package com.example.user.application.result;

import com.example.user.domain.model.UserProfile;

import java.time.Instant;
import java.util.UUID;

public record UserProfileSummaryResult(
        UUID userId,
        String email,
        String name,
        String nickname,
        String status,
        Instant createdAt
) {
    public static UserProfileSummaryResult from(UserProfile profile) {
        return new UserProfileSummaryResult(
                profile.getUserId(),
                profile.getEmail().value(),
                profile.getName(),
                profile.getNickname(),
                profile.getStatus().name(),
                profile.getCreatedAt()
        );
    }
}
