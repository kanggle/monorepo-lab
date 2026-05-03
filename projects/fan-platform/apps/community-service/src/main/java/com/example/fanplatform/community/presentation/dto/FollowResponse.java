package com.example.fanplatform.community.presentation.dto;

import com.example.fanplatform.community.application.FollowArtistUseCase;

import java.time.Instant;

public record FollowResponse(
        String fanAccountId,
        String artistAccountId,
        String tenantId,
        Instant followedAt
) {
    public static FollowResponse from(FollowArtistUseCase.FollowResult r) {
        return new FollowResponse(
                r.fanAccountId(), r.artistAccountId(),
                r.tenantId(), r.followedAt());
    }
}
