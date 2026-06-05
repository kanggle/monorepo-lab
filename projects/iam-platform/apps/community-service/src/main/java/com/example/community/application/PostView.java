package com.example.community.application;

import com.example.community.domain.post.PostType;
import com.example.community.domain.post.PostVisibility;
import com.example.community.domain.post.status.PostStatus;

import java.time.Instant;

public record PostView(
        String postId,
        PostType type,
        PostVisibility visibility,
        PostStatus status,
        String authorAccountId,
        String authorDisplayName,
        String title,
        String body,
        long commentCount,
        long reactionCount,
        String myReaction,
        Instant publishedAt,
        Instant createdAt
) {
}
