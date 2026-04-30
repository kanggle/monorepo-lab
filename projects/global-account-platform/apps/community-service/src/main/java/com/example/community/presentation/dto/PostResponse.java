package com.example.community.presentation.dto;

import com.example.community.application.PostView;

import java.time.Instant;

public record PostResponse(
        String postId,
        String type,
        String visibility,
        String status,
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
    public static PostResponse from(PostView v) {
        return new PostResponse(
                v.postId(),
                v.type().name(),
                v.visibility().name(),
                v.status().name(),
                v.authorAccountId(),
                v.authorDisplayName(),
                v.title(),
                v.body(),
                v.commentCount(),
                v.reactionCount(),
                v.myReaction(),
                v.publishedAt(),
                v.createdAt()
        );
    }
}
