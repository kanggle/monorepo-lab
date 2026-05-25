package com.example.fanplatform.community.infrastructure.jpa;

import com.example.fanplatform.community.domain.reaction.Reaction;
import com.example.fanplatform.community.domain.reaction.ReactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ReactionRepositoryImpl implements ReactionRepository {

    private final ReactionJpaRepository jpa;

    @Override
    public Optional<Reaction> find(String postId, String reactorAccountId, String tenantId) {
        return jpa.findByPostIdAndReactorAccountIdAndTenantId(postId, reactorAccountId, tenantId);
    }

    @Override
    public Reaction save(Reaction reaction) {
        return jpa.save(reaction);
    }

    @Override
    public void delete(Reaction reaction) {
        jpa.delete(reaction);
    }

    @Override
    public long countByPostId(String postId, String tenantId) {
        return jpa.countByPostIdAndTenantId(postId, tenantId);
    }

    @Override
    public Map<String, Long> countsByPostIds(List<String> postIds, String tenantId) {
        if (postIds == null || postIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return jpa.countsGroupedByPostId(postIds, tenantId).stream()
                .collect(Collectors.toMap(
                        ReactionJpaRepository.PostIdCount::getPostId,
                        ReactionJpaRepository.PostIdCount::getCnt));
    }
}
