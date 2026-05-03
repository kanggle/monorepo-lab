package com.example.fanplatform.community.application;

import com.example.fanplatform.community.domain.post.PostType;
import com.example.fanplatform.community.domain.post.PostVisibility;

import java.time.Instant;

public record FeedItemView(
        String postId,
        PostType postType,
        PostVisibility visibility,
        String authorAccountId,
        String title,
        String bodyPreview,
        long commentCount,
        long reactionCount,
        Instant publishedAt,
        boolean locked
) {
}
