package com.example.fanplatform.community.domain.comment;

import com.example.common.page.PageResult;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface CommentRepository {

    Comment save(Comment comment);

    Optional<Comment> findById(String id, String tenantId);

    long countByPostId(String postId, String tenantId);

    /**
     * Bulk count of non-deleted comments grouped by postId (tenant-scoped).
     * Returns an empty map for an empty input (avoids invalid SQL IN ()).
     */
    Map<String, Long> countsByPostIds(List<String> postIds, String tenantId);

    /**
     * Paged, non-deleted comments on one post within one tenant, oldest first
     * (TASK-FAN-BE-052). Visibility/membership gating is NOT this repository's
     * concern — the caller ({@code GetCommentsUseCase}) must run
     * {@code PostAccessGuard.requirePublishedAccess} before calling this.
     */
    PageResult<Comment> findByPostId(String postId, String tenantId, int page, int size);
}
