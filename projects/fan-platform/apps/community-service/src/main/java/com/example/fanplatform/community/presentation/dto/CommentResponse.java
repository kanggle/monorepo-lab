package com.example.fanplatform.community.presentation.dto;

import com.example.fanplatform.community.application.AddCommentUseCase;
import com.example.fanplatform.community.domain.comment.Comment;

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

    /** List path (TASK-FAN-BE-052) — maps straight from the domain entity. */
    public static CommentResponse from(Comment c) {
        return new CommentResponse(
                c.getId(), c.getPostId(), c.getTenantId(),
                c.getAuthorAccountId(), c.getBody(), c.getCreatedAt());
    }
}
