package com.example.fanplatform.community.presentation.dto;

import com.example.fanplatform.community.application.FeedItemView;

import java.time.Instant;
import java.util.List;

public record FeedItemResponse(
        String postId,
        String postType,
        String visibility,
        String authorAccountId,
        String title,
        String bodyPreview,
        List<String> mediaRefs,
        long commentCount,
        long reactionCount,
        Instant publishedAt,
        boolean locked
) {
    public static FeedItemResponse from(FeedItemView v) {
        return new FeedItemResponse(
                v.postId(),
                v.postType().name(),
                v.visibility().name(),
                v.authorAccountId(),
                v.title(),
                v.bodyPreview(),
                v.mediaRefs(),
                v.commentCount(),
                v.reactionCount(),
                v.publishedAt(),
                v.locked());
    }
}
