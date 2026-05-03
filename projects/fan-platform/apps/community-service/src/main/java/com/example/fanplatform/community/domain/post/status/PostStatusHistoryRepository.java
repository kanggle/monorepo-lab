package com.example.fanplatform.community.domain.post.status;

/**
 * Domain port for persisting post status transitions.
 * Implementation lives in the infrastructure layer (append-only).
 */
public interface PostStatusHistoryRepository {

    void save(PostStatusHistoryEntry entry);
}
