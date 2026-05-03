package com.example.fanplatform.community.presentation.dto;

import com.example.fanplatform.community.application.PostView;

import java.time.Instant;

public record PostResponse(
        String postId,
        String tenantId,
        String postType,
        String visibility,
        String status,
        String authorAccountId,
        String title,
        String body,
        long commentCount,
        long reactionCount,
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
                v.commentCount(),
                v.reactionCount(),
                v.publishedAt(),
                v.createdAt(),
                v.updatedAt());
    }
}
