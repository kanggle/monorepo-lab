package com.example.fanplatform.community.domain.reaction;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ReactionRepository {

    Optional<Reaction> find(String postId, String reactorAccountId, String tenantId);

    Reaction save(Reaction reaction);

    void delete(Reaction reaction);

    long countByPostId(String postId, String tenantId);

    Map<String, Long> countsByPostIds(List<String> postIds, String tenantId);
}
