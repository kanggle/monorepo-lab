package com.example.community.presentation.dto;

import java.time.Instant;

public record FollowResponse(
        String fanAccountId,
        String artistAccountId,
        Instant followedAt
) {
}
