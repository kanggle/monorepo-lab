package com.example.community.domain.reaction;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ReactionRepository {

    Optional<Reaction> find(String postId, String accountId);

    Reaction save(Reaction reaction);

    long countByPostId(String postId);

    /**
     * Bulk aggregate count of reactions grouped by postId.
     * Returns an empty map if {@code postIds} is empty (prevents invalid SQL IN ()).
     */
    Map<String, Long> countsByPostIds(List<String> postIds);
}
