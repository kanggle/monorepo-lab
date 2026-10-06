package com.example.fanplatform.community.presentation.dto;

import com.example.common.page.PageResult;
import com.example.fanplatform.community.domain.comment.Comment;

import java.util.List;

/**
 * Paginated envelope for {@code GET /api/community/posts/{postId}/comments} (TASK-FAN-BE-052).
 *
 * <p>Items reuse {@link CommentResponse} — the same shape the {@code POST} response returns —
 * so a client that renders one comment can render this list without a second mapping. The page
 * fields mirror {@link MyPostsResponse}, including the derived {@code hasNext}.
 */
public record CommentListResponse(
        List<CommentResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    public static CommentListResponse from(PageResult<Comment> p) {
        return new CommentListResponse(
                p.content().stream().map(CommentResponse::from).toList(),
                p.page(),
                p.size(),
                p.totalElements(),
                p.totalPages(),
                p.page() + 1 < p.totalPages());
    }
}
