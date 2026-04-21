package com.example.user.presentation.dto.response;

import com.example.user.application.result.UserProfileSummaryResult;

import java.time.Instant;
import java.util.UUID;

public record UserProfileSummaryResponse(
        UUID userId,
        String email,
        String name,
        String nickname,
        String status,
        Instant createdAt
) {
    public static UserProfileSummaryResponse from(UserProfileSummaryResult result) {
        return new UserProfileSummaryResponse(
                result.userId(),
                result.email(),
                result.name(),
                result.nickname(),
                result.status(),
                result.createdAt()
        );
    }
}
