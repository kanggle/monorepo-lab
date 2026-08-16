package com.example.fanplatform.community.presentation.dto;

import com.example.fanplatform.community.application.IsFollowingArtistUseCase;

/**
 * Response for {@code GET /api/community/follows/{artistAccountId}} (TASK-FAN-BE-049).
 *
 * <p>Echoes the queried id back rather than answering with a bare boolean: the
 * caller renders one button per artist and the id is what tells two in-flight
 * answers apart.
 */
public record FollowStatusResponse(
        String artistAccountId,
        boolean following
) {
    public static FollowStatusResponse from(IsFollowingArtistUseCase.FollowStatus s) {
        return new FollowStatusResponse(s.artistAccountId(), s.following());
    }
}
