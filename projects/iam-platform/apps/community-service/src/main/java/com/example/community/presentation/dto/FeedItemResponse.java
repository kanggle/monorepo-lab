package com.example.community.presentation.dto;

import com.example.community.application.FeedItemView;

import java.time.Instant;

public record FeedItemResponse(
        String postId,
        String type,
        String visibility,
        String authorAccountId,
        String authorDisplayName,
        String title,
        String bodyPreview,
        long commentCount,
        long reactionCount,
        Instant publishedAt,
        boolean locked
) {
    public static FeedItemResponse from(FeedItemView v) {
        return new FeedItemResponse(
                v.postId(),
                v.type().name(),
                v.visibility().name(),
                v.authorAccountId(),
                v.authorDisplayName(),
                v.title(),
                v.bodyPreview(),
                v.commentCount(),
                v.reactionCount(),
                v.publishedAt(),
                v.locked()
        );
    }
}
