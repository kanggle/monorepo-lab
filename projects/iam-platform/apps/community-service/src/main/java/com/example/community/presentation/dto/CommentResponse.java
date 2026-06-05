package com.example.community.presentation.dto;

import com.example.community.application.AddCommentUseCase;

import java.time.Instant;

public record CommentResponse(
        String commentId,
        String postId,
        String authorAccountId,
        String authorDisplayName,
        String body,
        Instant createdAt
) {
    public static CommentResponse from(AddCommentUseCase.CommentView v) {
        return new CommentResponse(
                v.commentId(),
                v.postId(),
                v.authorAccountId(),
                v.authorDisplayName(),
                v.body(),
                v.createdAt()
        );
    }
}
