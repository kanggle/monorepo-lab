package com.example.community.application;

import com.example.community.domain.post.PostType;
import com.example.community.domain.post.PostVisibility;

import java.time.Instant;

public record FeedItemView(
        String postId,
        PostType type,
        PostVisibility visibility,
        String authorAccountId,
        String authorDisplayName,
        String title,
        String bodyPreview,
        long commentCount,
        long reactionCount,
        Instant publishedAt,
        boolean locked
) {
}
