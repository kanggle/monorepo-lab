package com.example.fanplatform.community.domain.post;

import java.util.Optional;

/**
 * Domain port for persisting and retrieving {@link Post} aggregates.
 *
 * <p>Multi-tenant: every read is scoped by {@code tenantId}. Cross-tenant
 * misuse is reported as {@code Optional.empty()} (NOT 403) so callers cannot
 * differentiate "post does not exist" from "post belongs to another tenant".
 */
public interface PostRepository {

    Post save(Post post);

    Optional<Post> findById(String id, String tenantId);

    /**
     * Returns posts authored by accounts the {@code fanAccountId} follows,
     * scoped to {@code tenantId}, status=PUBLISHED, deleted_at IS NULL.
     */
    PageResult<Post> findFeedForFan(String fanAccountId, String tenantId, int page, int size);
}
