package com.example.fanplatform.community.presentation.dto;

import com.example.fanplatform.community.application.AddCommentUseCase;

import java.time.Instant;

public record CommentResponse(
        String commentId,
        String postId,
        String tenantId,
        String authorAccountId,
        String body,
        Instant createdAt
) {
    public static CommentResponse from(AddCommentUseCase.CommentView v) {
        return new CommentResponse(
                v.commentId(), v.postId(), v.tenantId(),
                v.authorAccountId(), v.body(), v.createdAt());
    }
}
