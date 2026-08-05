package com.example.fanplatform.community.presentation.dto;

import com.example.common.page.PageResult;
import com.example.fanplatform.community.application.PostView;

import java.util.List;

/**
 * Paginated envelope for {@code GET /api/community/posts/mine} (TASK-FAN-FE-016).
 *
 * <p>Items reuse {@link PostResponse} — the same shape the publish and get routes return — so
 * a client that renders one post can render this list without a second mapping. The page
 * fields mirror {@link FeedResponse}, including the derived {@code hasNext}.
 */
public record MyPostsResponse(
        List<PostResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    public static MyPostsResponse from(PageResult<PostView> p) {
        return new MyPostsResponse(
                p.content().stream().map(PostResponse::from).toList(),
                p.page(),
                p.size(),
                p.totalElements(),
                p.totalPages(),
                p.page() + 1 < p.totalPages());
    }
}
