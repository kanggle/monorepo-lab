package com.example.fanplatform.community.domain.comment;

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
}
