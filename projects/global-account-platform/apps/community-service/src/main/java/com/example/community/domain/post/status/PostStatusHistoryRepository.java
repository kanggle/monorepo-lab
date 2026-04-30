package com.example.community.domain.post.status;

/**
 * Domain port for persisting post status transitions.
 * Implementation lives in infrastructure layer.
 */
public interface PostStatusHistoryRepository {

    void save(PostStatusHistoryEntry entry);
}
