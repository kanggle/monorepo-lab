package com.example.fanplatform.community.application;

import com.example.fanplatform.community.domain.post.PostType;
import com.example.fanplatform.community.domain.post.PostVisibility;

import java.time.Instant;
import java.util.List;

public record FeedItemView(
        String postId,
        PostType postType,
        PostVisibility visibility,
        String authorAccountId,
        String title,
        String bodyPreview,
        /** Absolute https URLs; empty (never {@code null}) when {@code locked} (TASK-MONO-679). */
        List<String> mediaRefs,
        long commentCount,
        long reactionCount,
        Instant publishedAt,
        boolean locked
) {
}
