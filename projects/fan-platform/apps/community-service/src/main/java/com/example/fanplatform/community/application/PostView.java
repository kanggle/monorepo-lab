package com.example.fanplatform.community.application;

import com.example.fanplatform.community.domain.post.PostType;
import com.example.fanplatform.community.domain.post.PostVisibility;
import com.example.fanplatform.community.domain.post.status.PostStatus;

import java.time.Instant;
import java.util.List;

public record PostView(
        String postId,
        String tenantId,
        PostType postType,
        PostVisibility visibility,
        PostStatus status,
        String authorAccountId,
        String title,
        String body,
        /** Absolute https URLs, never {@code null} (TASK-MONO-679). */
        List<String> mediaRefs,
        long commentCount,
        long reactionCount,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
