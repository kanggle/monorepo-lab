package com.example.community.domain.comment;

import java.util.List;
import java.util.Map;

public interface CommentRepository {

    Comment save(Comment comment);

    long countByPostId(String postId);

    /**
     * Bulk aggregate count of non-deleted comments grouped by postId.
     * Returns an empty map if {@code postIds} is empty (prevents invalid SQL IN ()).
     */
    Map<String, Long> countsByPostIds(List<String> postIds);
}
