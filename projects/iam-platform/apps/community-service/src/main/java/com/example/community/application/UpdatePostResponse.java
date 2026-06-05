package com.example.community.application;

import java.time.Instant;
import java.util.List;

/**
 * Application-layer result returned by {@link UpdatePostUseCase} after a successful content update.
 */
public record UpdatePostResponse(
        String postId,
        String title,
        String body,
        List<String> mediaUrls,
        Instant updatedAt
) {
}
