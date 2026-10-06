package com.example.fanplatform.community.presentation.dto;

import com.example.fanplatform.community.application.PostView;

import java.time.Instant;
import java.util.List;

public record PostResponse(
        String postId,
        String tenantId,
        String postType,
        String visibility,
        String status,
        String authorAccountId,
        String title,
        String body,
        List<String> mediaRefs,
        long commentCount,
        long reactionCount,
        /**
         * The caller's own reaction, or {@code null} (TASK-FAN-BE-051). Populated
         * only on {@code GET /api/community/posts/{id}} — see {@code
         * community-api.md} § {@code myReaction}.
         */
        String myReaction,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static PostResponse from(PostView v) {
        return new PostResponse(
                v.postId(), v.tenantId(),
                v.postType().name(),
                v.visibility().name(),
                v.status().name(),
                v.authorAccountId(),
                v.title(),
                v.body(),
                v.mediaRefs(),
                v.commentCount(),
                v.reactionCount(),
                v.myReaction() == null ? null : v.myReaction().name(),
                v.publishedAt(),
                v.createdAt(),
                v.updatedAt());
    }
}
